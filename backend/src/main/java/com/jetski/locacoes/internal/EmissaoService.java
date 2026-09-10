package com.jetski.locacoes.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.DocumentoEmitido;
import com.jetski.locacoes.domain.EnvioStatus;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaAceite;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.DocumentoEmitidoRepository;
import com.jetski.locacoes.internal.repository.ReservaAceiteRepository;
import com.jetski.locacoes.internal.repository.ReservaHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.locacoes.event.DocumentoPreviewGeradoEvent;
import com.jetski.locacoes.event.EnvioDocumentosSolicitadoEvent;
import com.jetski.reservas.domain.event.DocumentosEmitidosEvent;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.storage.StorageService;
import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.DocumentoConfig;
import com.jetski.tenant.domain.Tenant;
import lombok.Builder;
import lombok.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Orquestra a emissão dos documentos do balcão (F2.6):
 * monta os dados → gera o PDF consolidado → arquiva → registra → envia
 * (Marinha + cliente) → publica evento.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmissaoService {

    private final ReservaRepository reservaRepository;
    private final CustomerHabilitacaoSyncService customerHabilitacaoSyncService;
    private final ClienteNotificacaoService clienteNotificacaoService;
    private final ClienteRepository clienteRepository;
    private final com.jetski.locacoes.internal.repository.InstrutorRepository instrutorRepository;
    private final ReservaHabilitacaoRepository habilitacaoRepository;
    private final ReservaAceiteRepository aceiteRepository;
    private final DocumentoEmitidoRepository documentoRepository;
    private final StorageService storageService;
    private final TenantQueryService tenantQueryService;
    private final DocumentoPdfService documentoPdfService;
    private final ClienteAnexoService clienteAnexoService;
    private final com.jetski.creditos.CreditoService creditoService;
    private final com.jetski.tenant.PlanoLimiteService planoLimiteService;
    private final VinculoEmissaoService vinculoEmissaoService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final com.jetski.shared.assinatura.CarimboTempoService carimboTempoService;
    private final PadesSignatureService padesSignatureService;
    private final DocumentoEnvioService documentoEnvioService;
    private final EmissaoEnvioConfigService envioConfigService;
    private final EmissaoLockService emissaoLockService;

    @Value
    @Builder
    public static class ResultadoEmissao {
        UUID documentoId;
        String s3Key;
        String hashSha256;
        String downloadUrl;
        String gruNumero;
        String gruValor;
        boolean enviadoMarinha;
        boolean enviadoCliente;
        /** Estado do envio por destino (V065) — é o que a tela acompanha por polling. */
        EnvioStatus marinhaEnvioStatus;
        EnvioStatus clienteEnvioStatus;
        boolean docCompleta;
        java.util.List<String> pendencias;
        /**
         * Devolvemos um documento que já existia em vez de emitir outro (idempotência).
         * A tela mostra "já emitidos" e não anuncia uma segunda emissão que não houve.
         */
        boolean reaproveitado;
    }

    @Transactional
    public ResultadoEmissao emitir(UUID reservaId) {
        return emitir(reservaId, false);
    }

    /**
     * @param reemitir força uma emissão nova mesmo já havendo documento para a reserva
     *                 — gera outro PDF e <b>debita outro crédito</b>. Sem isto, uma
     *                 segunda chamada devolve o documento existente (idempotência).
     */
    @Transactional
    public ResultadoEmissao emitir(UUID reservaId, boolean reemitir) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + reservaId));

        // Um F5 no meio dos 25 s da emissão antiga gerava um SEGUNDO documento e um
        // SEGUNDO débito de crédito. O lock serializa cliques concorrentes; a checagem
        // logo abaixo cobre o caso sequencial, que foi o do incidente.
        emissaoLockService.lockReserva(reservaId);
        java.util.List<DocumentoEmitido> jaEmitidos = documentoRepository
            .findByTenantIdAndReservaIdOrderByEmitidoEmDesc(reserva.getTenantId(), reservaId);
        if (!reemitir && !jaEmitidos.isEmpty()) {
            return reaproveitar(reserva, jaEmitidos.get(0));
        }
        boolean reemissao = !jaEmitidos.isEmpty();

        ReservaHabilitacao hab = habilitacaoRepository.findByReservaId(reservaId)
            .orElseThrow(() -> new BusinessException("Habilitação não registrada para a reserva"));
        if (!Boolean.TRUE.equals(hab.getResolvida())) {
            throw new BusinessException("Habilitação não resolvida (CHA pendente ou GRU não paga)");
        }

        ReservaAceite aceite = aceiteRepository.findFirstByReservaIdOrderByAceitoEmDesc(reservaId)
            .orElseThrow(() -> new BusinessException("Termos não assinados (aceite ausente)"));

        Cliente cliente = clienteRepository.findById(reserva.getClienteId())
            .orElseThrow(() -> new NotFoundException("Cliente não encontrado: " + reserva.getClienteId()));

        Tenant tenant = tenantQueryService.findById(reserva.getTenantId());
        if (tenant == null) {
            throw new NotFoundException("Tenant não encontrado: " + reserva.getTenantId());
        }

        byte[] assinatura = lerAssinatura(aceite.getAssinaturaS3Key());

        // CHA = cliente já habilitado: não há documentação NORMAM nem envio à Marinha.
        boolean marinhaAplicavel = hab.getVia() == ReservaHabilitacao.Via.EMA;

        // Emissão delegada (V048): sem EMISSAO_PROPRIA no plano, a documentação
        // NORMAM sai em nome da EAMA parceira — vínculo ATIVO obrigatório,
        // identidade/instrutor/Capitania de destino são do EMISSOR.
        VinculoEmissaoService.DelegacaoContext delegacao =
            resolverDelegacao(reserva.getTenantId(), hab, marinhaAplicavel);

        // Portão duplo da emissão PRÓPRIA (§8.K, V050): além do módulo no plano,
        // exige o cadastro validado pelo superadmin. Lojas pré-existentes foram
        // grandfathered na V050; delegada valida a habilitação DO EMISSOR no vínculo.
        if (marinhaAplicavel && delegacao == null
                && !Boolean.TRUE.equals(tenant.getEmissoraHabilitada())) {
            throw new BusinessException("Sua empresa ainda não está habilitada como EAMA emissora "
                + "junto ao Meu Jet. Preencha o perfil de emissão (capitania + registro EAMA) na "
                + "tela Emissão delegada e solicite a validação — ou emita via EAMA parceira.");
        }

        DocumentoPdfService.DadosDocumento dados = montarDados(reserva, cliente, hab, tenant, delegacao);
        DocumentoConfig cfg = configDocumento(tenant);

        // Créditos: só o documento com destino à Marinha consome. Fail-fast ANTES do
        // trabalho pesado (PDF/carimbo/assinatura); o débito definitivo vem após o save.
        if (marinhaAplicavel) {
            creditoService.verificarSaldoDisponivel(reserva.getTenantId());
        }

        // PDFs por destino: a Marinha pode receber um recorte diferente do cliente
        // (ex.: sem o Termo de Responsabilidade), conforme a parametrização do tenant.
        DocumentoPdfService.DocumentoPdf pdfCliente =
            gerarParaDestino(dados, assinatura, cliente.getId(), hab, cfg.cliente(), null);
        DocumentoPdfService.DocumentoPdf pdfMarinha = marinhaAplicavel
            ? gerarParaDestino(dados, assinatura, cliente.getId(), hab, cfg.marinha(), null)
            : null;

        // Reforço jurídico (Fase A): trilha de auditoria + carimbo de tempo por documento.
        com.jetski.tenant.domain.AssinaturaConfig cfgAss =
            (tenant.getAssinaturaConfig() != null ? tenant.getAssinaturaConfig()
                                                  : com.jetski.tenant.domain.AssinaturaConfig.padrao()).comDefaults();
        if (cfgAss.paginaAuditoriaOn()) {
            pdfCliente = comAuditoria(pdfCliente, cliente, aceite, hab, cfgAss);
            if (pdfMarinha != null) pdfMarinha = comAuditoria(pdfMarinha, cliente, aceite, hab, cfgAss);
        }
        // Assinatura digital PAdES (por último — deve ser a operação final sobre o PDF).
        // Configurável por destino: na cópia da Marinha o cert auto-assinado gera aviso
        // de "validade desconhecida", então normalmente fica só na cópia do cliente.
        if (cfgAss.padesClienteOn()) {
            pdfCliente = comAssinaturaDigital(pdfCliente, cfgAss);
        }
        if (pdfMarinha != null && cfgAss.padesMarinhaOn()) {
            pdfMarinha = comAssinaturaDigital(pdfMarinha, cfgAss);
        }

        // Canônico p/ download/consulta = visão do cliente (completa). Marinha à parte.
        // Reemissão usa key própria: a key fixa sobrescreveria o PDF da emissão
        // anterior, deixando a linha antiga apontando para um arquivo cujo
        // hash_sha256 já não confere — trilha e carimbo PAdES corrompidos.
        String key = String.format("%s/reserva/%s/documento%s.pdf", reserva.getTenantId(), reservaId,
            reemissao ? "-" + Instant.now().toEpochMilli() : "");
        storageService.putObject(key, pdfCliente.conteudo(), "application/pdf");
        if (pdfMarinha != null) {
            storageService.putObject(keyMarinha(key),
                pdfMarinha.conteudo(), "application/pdf");
        }

        // Delegada: o destino Marinha é o e-mail do EMISSOR (editável pela EAMA, §8.E)
        String marinhaEmail = delegacao != null ? delegacao.marinhaEmail() : tenant.getMarinhaEmail();
        String clienteEmail = cliente.getEmail();

        DocumentoEmitido doc = documentoRepository.save(DocumentoEmitido.builder()
            .tenantId(reserva.getTenantId())
            .reservaId(reservaId)
            .s3Key(key)
            .hashSha256(pdfCliente.sha256())
            .destinos(destinosJson(marinhaEmail, clienteEmail))
            .emitidoEm(Instant.now())
            .emissorTenantId(delegacao != null ? delegacao.emissorTenantId() : null)
            .emissorSnapshot(delegacao != null ? snapshotEmissor(delegacao) : null)
            .build());

        // Débito síncrono na mesma transação: sem saldo, a emissão inteira reverte
        // (nem a via do cliente sai). Advisory lock por tenant impede corrida.
        // Delegada: o crédito é da OPERADORA (§8.C) — nada muda aqui.
        if (marinhaAplicavel) {
            creditoService.debitarEmissaoDocumento(reserva.getTenantId(), doc.getId(), reservaId);
        }

        // Espelho no tenant emissor (§3.5): a trilha da EAMA, na mesma transação.
        if (delegacao != null) {
            vinculoEmissaoService.registrarEspelho(delegacao, doc.getId(), pdfCliente.sha256(),
                keyMarinha(key), tenant.getRazaoSocial(),
                cliente.getNome(), cliente.getDocumento(), hab.getGruNumero(), doc.getEmitidoEm());
        }

        // Documentação completa? Só com tudo cumprido a Marinha pode receber o e-mail.
        java.util.List<String> pendencias = pendenciasDocumentacao(reserva.getTenantId(), hab, cliente, cfg);
        boolean docCompleta = pendencias.isEmpty();

        // Finaliza o atendimento: RASCUNHO/PENDENTE/CONFIRMADA → CONFIRMADA (completo)
        // ou PENDENTE (faltando algo). É aqui que o rascunho vira reserva "real".
        if (reserva.getStatus() == Reserva.ReservaStatus.RASCUNHO
                || reserva.getStatus() == Reserva.ReservaStatus.PENDENTE
                || reserva.getStatus() == Reserva.ReservaStatus.CONFIRMADA) {
            reserva.setStatus(docCompleta
                ? Reserva.ReservaStatus.CONFIRMADA : Reserva.ReservaStatus.PENDENTE);
        }
        reserva.setDocumentoEmitidoEm(Instant.now());
        reservaRepository.save(reserva);

        // Habilitação temporária é dado DO CLIENTE — espelha no registro global
        // (sobrevive a reset/exclusão da loja). Best-effort na mesma transação.
        customerHabilitacaoSyncService.sync(reservaId);

        // Estado inicial do envio (V065): o que já se sabe antes de tentar. É isto que
        // o worker lê para decidir o que mandar — a linha do documento é a fila.
        EnvioStatus marinhaInicial = !marinhaAplicavel ? EnvioStatus.NAO_APLICAVEL
            : !docCompleta                            ? EnvioStatus.BLOQUEADO
            : vazio(marinhaEmail)                     ? EnvioStatus.SEM_DESTINATARIO
            :                                           EnvioStatus.PENDENTE;
        EnvioStatus clienteInicial = vazio(clienteEmail)
            ? EnvioStatus.SEM_DESTINATARIO : EnvioStatus.PENDENTE;
        doc.setMarinhaEnvioStatus(marinhaInicial);
        doc.setClienteEnvioStatus(clienteInicial);
        doc.setEnvioAtualizadoEm(Instant.now());
        if (!docCompleta) {
            log.info("Marinha NÃO notificada (reserva {}): pendências {}", reservaId, pendencias);
        }

        // O SMTP custava ~24 s dos ~25 s da emissão, DENTRO desta transação. No modo
        // assíncrono (default) o envio sai do request e a tela acompanha por status.
        DocumentoEnvioService.ResultadoEnvio envio;
        if (envioConfigService.assincrono()) {
            envio = new DocumentoEnvioService.ResultadoEnvio(marinhaInicial, null, clienteInicial, null);
            eventPublisher.publishEvent(new EnvioDocumentosSolicitadoEvent(
                reserva.getTenantId(), doc.getId(), reservaId, TenantContext.getUsuarioId()));
        } else {
            envio = documentoEnvioService.despachar(documentoEnvioService.contextoDaEmissao(
                doc.getId(), reserva, tenant, cliente, hab, cfg, delegacao,
                pdfCliente.conteudo(), pdfCliente.sha256(),
                pdfMarinha != null ? pdfMarinha.conteudo() : null,
                marinhaInicial, clienteInicial));
            documentoEnvioService.aplicar(doc, envio);
        }
        boolean enviadoMarinha = envio.enviadoMarinha();
        boolean enviadoCliente = envio.enviadoCliente();

        clienteNotificacaoService.notificar(reserva.getTenantId(), reserva.getClienteId(),
            com.jetski.locacoes.domain.ClienteNotificacao.DOCUMENTOS_EMITIDOS,
            "Seus documentos foram emitidos 🎉",
            "A documentação da sua habilitação foi emitida pela loja e enviada por e-mail.",
            "/conta/reservas/" + reservaId + "/habilitacao");

        // A notificação à EAMA emissora saiu daqui: agora faz parte do despacho
        // (DocumentoEnvioService), para valer também no modo assíncrono. O endereço
        // vem do emissor_snapshot — por isso ele passou a gravar contatoEmail.

        eventPublisher.publishEvent(DocumentosEmitidosEvent.of(
            reserva.getTenantId(), reservaId, doc.getId(),
            destinosResumo(marinhaInicial, clienteInicial), TenantContext.getUsuarioId(),
            delegacao != null ? delegacao.emissorTenantId() : null));

        String downloadUrl = storageService.generatePresignedDownloadUrl(key, 15).getUrl();
        log.info("Documentos emitidos: reservaId={}, docId={}, marinha={}, cliente={}",
            reservaId, doc.getId(), enviadoMarinha, enviadoCliente);

        return ResultadoEmissao.builder()
            .documentoId(doc.getId())
            .s3Key(key)
            .hashSha256(pdfCliente.sha256())
            .downloadUrl(downloadUrl)
            .gruNumero(hab.getGruNumero())
            .gruValor(hab.getGruValor() != null ? hab.getGruValor().toPlainString() : null)
            .enviadoMarinha(enviadoMarinha)
            .enviadoCliente(enviadoCliente)
            .marinhaEnvioStatus(doc.getMarinhaEnvioStatus())
            .clienteEnvioStatus(doc.getClienteEnvioStatus())
            .docCompleta(docCompleta)
            .pendencias(pendencias)
            .reaproveitado(false)
            .build();
    }

    /**
     * Devolve o documento que já existe para a reserva — sem gerar PDF, sem debitar
     * crédito e sem reenviar e-mail. As pendências são recalculadas: podem ter mudado
     * desde a emissão, e é informação útil para quem voltou à tela.
     */
    private ResultadoEmissao reaproveitar(Reserva reserva, DocumentoEmitido doc) {
        ReservaHabilitacao hab = habilitacaoRepository.findByReservaId(reserva.getId()).orElse(null);
        Tenant tenant = tenantQueryService.findById(reserva.getTenantId());
        Cliente cliente = reserva.getClienteId() == null ? null
            : clienteRepository.findById(reserva.getClienteId()).orElse(null);
        java.util.List<String> pendencias = (hab != null && cliente != null && tenant != null)
            ? pendenciasDocumentacao(reserva.getTenantId(), hab, cliente, configDocumento(tenant))
            : java.util.List.of();
        log.info("Emissão reaproveitada (idempotência): reserva={}, docId={}", reserva.getId(), doc.getId());
        return ResultadoEmissao.builder()
            .documentoId(doc.getId())
            .s3Key(doc.getS3Key())
            .hashSha256(doc.getHashSha256())
            .downloadUrl(storageService.generatePresignedDownloadUrl(doc.getS3Key(), 15).getUrl())
            .gruNumero(hab != null ? hab.getGruNumero() : null)
            .gruValor(hab != null && hab.getGruValor() != null ? hab.getGruValor().toPlainString() : null)
            .enviadoMarinha(doc.getMarinhaEnvioStatus() == EnvioStatus.ENVIADO)
            .enviadoCliente(doc.getClienteEnvioStatus() == EnvioStatus.ENVIADO)
            .marinhaEnvioStatus(doc.getMarinhaEnvioStatus())
            .clienteEnvioStatus(doc.getClienteEnvioStatus())
            .docCompleta(pendencias.isEmpty())
            .pendencias(pendencias)
            .reaproveitado(true)
            .build();
    }

    /**
     * Itens exigidos p/ liberar a Marinha. A habilitação resolvida (CHA/GRU) é
     * sempre exigida; os demais itens (EMA) são parametrizados por tenant em
     * {@link DocumentoConfig.ObrigatoriosMarinha}.
     */
    private java.util.List<String> pendenciasDocumentacao(UUID tenantId, ReservaHabilitacao hab, Cliente cliente,
            DocumentoConfig cfg) {
        DocumentoConfig.ObrigatoriosMarinha obr = cfg.obrigatoriosMarinha();
        java.util.List<String> p = new java.util.ArrayList<>();
        if (!Boolean.TRUE.equals(hab.getResolvida())) {
            p.add(hab.getVia() == ReservaHabilitacao.Via.CHA ? "CHA não informada" : "GRU não paga");
        }
        if (hab.getVia() == ReservaHabilitacao.Via.EMA) {
            if (obr.identidadeReq() && !anexoPresente(cliente.getId(),
                    com.jetski.locacoes.domain.ClienteAnexo.Tipo.IDENTIDADE)) p.add("Documento de identidade (RG/CNH)");
            if (obr.selfieReq() && !anexoPresente(cliente.getId(),
                    com.jetski.locacoes.domain.ClienteAnexo.Tipo.SELFIE)) p.add("Selfie/foto do cliente");
            if (obr.saudeReq() && !Boolean.TRUE.equals(hab.getAnexoSaude())) p.add("Autodeclaração de saúde (5-C)");
            if (obr.regrasReq() && !Boolean.TRUE.equals(hab.getAnexoRegras())) p.add("Anexo de regras");
            if (obr.residenciaReq() && !Boolean.TRUE.equals(hab.getAnexoResidencia())) p.add("Comprovante/Declaração de residência");
            if (obr.instrutorReq() && hab.getInstrutorId() == null) p.add("Instrutor");
            if (obr.nacionalidadeReq() && vazio(cliente.getNacionalidade())) p.add("Nacionalidade");
            if (obr.naturalidadeReq() && vazio(cliente.getNaturalidade())) p.add("Naturalidade");
            // Videoaula (V063): sempre exigida, salvo empresa com o módulo VIDEO_ORIENTACAO
            // que a desligou em Configurações — mesma fórmula do balcão/summary.
            boolean podeDesativar = planoLimiteService.moduloHabilitado(
                tenantId, com.jetski.tenant.ModuloPlano.VIDEO_ORIENTACAO);
            if (cfg.videoaulaExigida(podeDesativar) && hab.getVideoaulaEm() == null) {
                p.add("Videoaula de orientação não assistida");
            }
        }
        return p;
    }

    private static final java.time.format.DateTimeFormatter AUD_FMT =
        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(java.time.ZoneId.of("America/Sao_Paulo"));

    /** Anexa a página de trilha de auditoria (com carimbo de tempo) ao documento. */
    private DocumentoPdfService.DocumentoPdf comAuditoria(
            DocumentoPdfService.DocumentoPdf pdf, Cliente cliente, ReservaAceite aceite,
            ReservaHabilitacao hab, com.jetski.tenant.domain.AssinaturaConfig cfg) {
        try {
            var carimbo = carimboTempoService.carimbar(pdf.conteudo(), cfg.carimboOn(), cfg.tsaUrlOrDefault());
            String aceitoEm = aceite.getAceitoEm() != null ? AUD_FMT.format(aceite.getAceitoEm()) : "—";
            String carimboData = carimbo.getData() != null ? AUD_FMT.format(carimbo.getData()) : "—";
            String otpTxt = Boolean.TRUE.equals(aceite.getOtpVerificado())
                ? "Confirmado via " + (aceite.getOtpCanal() != null ? aceite.getOtpCanal() : "—")
                    + (aceite.getOtpDestino() != null ? " (" + aceite.getOtpDestino() + ")" : "")
                : null;
            DocumentoPdfService.DadosAuditoria aud = new DocumentoPdfService.DadosAuditoria(
                cliente.getNome(), cliente.getDocumento(), cliente.getEmail(), cliente.getTelefone(),
                aceitoEm, aceite.getIp(), aceite.getUserAgent(),
                aceite.getOperadorId() != null ? aceite.getOperadorId().toString() : "—",
                aceite.getOrigem(), aceite.getMetodo() != null ? aceite.getMetodo().name() : "—",
                Boolean.TRUE.equals(hab.getAnexoRegras()), descricaoVideoaula(hab),
                otpTxt,
                pdf.sha256(),
                carimbo.getFonte(), carimbo.getAutoridade(), carimboData, carimbo.getSerial());
            byte[] pagina = documentoPdfService.paginaAuditoria(aud).conteudo();
            return documentoPdfService.anexarPdf(pdf, pagina);
        } catch (Exception e) {
            log.warn("Página de auditoria não anexada (segue sem): {}", e.getMessage());
            return pdf;
        }
    }

    /** Texto da videoaula na página de auditoria: como, em que idioma e quando foi cumprida. */
    static String descricaoVideoaula(ReservaHabilitacao hab) {
        if (hab.getVideoaulaEm() == null) return "—";
        String quando = " em " + AUD_FMT.format(hab.getVideoaulaEm());
        if (hab.getVideoaulaModo() == ReservaHabilitacao.VideoaulaModo.PLAYER) {
            String idioma = hab.getVideoaulaIdioma() != null ? ", " + hab.getVideoaulaIdioma().toUpperCase() : "";
            return "Sim — assistida no balcão (player integrado" + idioma + ")" + quando;
        }
        return "Sim — declarada" + quando;
    }

    private static String sha256Hex(byte[] data) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Assina o PDF em PAdES (tamper-evident). Falha → segue com o PDF não assinado. */
    private DocumentoPdfService.DocumentoPdf comAssinaturaDigital(
            DocumentoPdfService.DocumentoPdf pdf, com.jetski.tenant.domain.AssinaturaConfig cfg) {
        try {
            byte[] assinado = padesSignatureService.assinar(
                pdf.conteudo(), cfg.carimboOn() ? cfg.tsaUrlOrDefault() : null);
            return new DocumentoPdfService.DocumentoPdf(assinado, sha256Hex(assinado));
        } catch (Exception e) {
            log.warn("PDF não assinado digitalmente (segue sem): {}", e.getMessage());
            return pdf;
        }
    }

    /** Há anexo do tipo informado para o cliente? */
    private boolean anexoPresente(UUID clienteId, com.jetski.locacoes.domain.ClienteAnexo.Tipo tipo) {
        try {
            return clienteAnexoService.buscar(clienteId, tipo).isPresent();
        } catch (Exception e) {
            log.warn("Falha ao checar anexo {} do cliente {}: {}", tipo, clienteId, e.getMessage());
            return false;
        }
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    /** Destino do documento — define o recorte de seções e a marca d'água da prévia. */
    public enum Destino { MARINHA, CLIENTE }

    private DocumentoConfig configDocumento(Tenant tenant) {
        return DocumentoEnvioService.configDocumento(tenant);
    }

    private String keyMarinha(String s3KeyCliente) {
        return DocumentoEnvioService.keyMarinhaDe(s3KeyCliente);
    }

    /**
     * Monta o PDF para um destino, aplicando o recorte de seções configurado
     * (1-C/5-C/5-B/Termo internos + anexos do cliente + comprovante da GRU).
     */
    private DocumentoPdfService.DocumentoPdf gerarParaDestino(
            DocumentoPdfService.DadosDocumento dados, byte[] assinatura, UUID clienteId,
            ReservaHabilitacao hab, DocumentoConfig.Destino cfg, String marcaDagua) {
        java.util.Set<DocumentoPdfService.Secao> secoes =
            java.util.EnumSet.noneOf(DocumentoPdfService.Secao.class);
        if (cfg.residenciaOn()) secoes.add(DocumentoPdfService.Secao.RESIDENCIA);
        if (cfg.saudeOn()) secoes.add(DocumentoPdfService.Secao.SAUDE);
        if (cfg.instrutorOn()) secoes.add(DocumentoPdfService.Secao.INSTRUTOR);
        if (cfg.termoOn()) secoes.add(DocumentoPdfService.Secao.TERMO);

        java.util.List<DocumentoPdfService.AnexoImagem> anexos = anexosDoCliente(clienteId, cfg);

        DocumentoPdfService.DocumentoPdf pdf =
            documentoPdfService.gerarDocumentoConsolidado(dados, assinatura, anexos, secoes, marcaDagua);

        if (cfg.comprovanteGruOn() && hab != null && hab.getGruComprovanteS3Key() != null) {
            try {
                byte[] comprovante = documentoPdfService.carimbarRodape(
                    storageService.getObject(hab.getGruComprovanteS3Key()),
                    "Comprovante de pagamento da GRU");
                pdf = documentoPdfService.anexarPdf(pdf, comprovante);
            } catch (Exception e) {
                log.warn("Comprovante da GRU não anexado ao PDF: {}", e.getMessage());
            }
        }
        return pdf;
    }

    /**
     * Gera, sem enviar nem persistir, a prévia do PDF que um destino receberá
     * (respeitando a parametrização do tenant). Carimba "RASCUNHO" enquanto a
     * documentação ainda tem pendências — útil quando a GRU não foi paga e os
     * documentos definitivos ainda não podem ser emitidos.
     */
    /** Prévia + nome do arquivo, para o link abrir com o nome do locatário. */
    public record PreviaPdf(byte[] conteudo, String filename) {}

    @Transactional(readOnly = true)
    public PreviaPdf previewNomeado(UUID reservaId, Destino destino) {
        Cliente cliente = reservaRepository.findById(reservaId)
            .flatMap(r -> clienteRepository.findById(r.getClienteId()))
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + reservaId));
        String prefixo = destino == Destino.MARINHA ? "Prévia Marinha" : "Prévia";
        return new PreviaPdf(preview(reservaId, destino),
            DocumentoNome.de(prefixo, cliente.getNome(), cliente.getDocumento()));
    }

    @Transactional(readOnly = true)
    public byte[] preview(UUID reservaId, Destino destino) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + reservaId));
        ReservaHabilitacao hab = habilitacaoRepository.findByReservaId(reservaId)
            .orElseThrow(() -> new BusinessException("Registre a habilitação antes de pré-visualizar"));
        Cliente cliente = clienteRepository.findById(reserva.getClienteId())
            .orElseThrow(() -> new NotFoundException("Cliente não encontrado: " + reserva.getClienteId()));
        Tenant tenant = tenantQueryService.findById(reserva.getTenantId());
        if (tenant == null) {
            throw new NotFoundException("Tenant não encontrado: " + reserva.getTenantId());
        }

        ReservaAceite aceite = aceiteRepository.findFirstByReservaIdOrderByAceitoEmDesc(reservaId).orElse(null);
        byte[] assinatura = (aceite != null) ? lerAssinatura(aceite.getAssinaturaS3Key()) : null;

        // Prévia também respeita a delegação: identidade/instrutor da EAMA parceira
        VinculoEmissaoService.DelegacaoContext delegacao = resolverDelegacao(
            reserva.getTenantId(), hab, hab.getVia() == ReservaHabilitacao.Via.EMA);
        DocumentoPdfService.DadosDocumento dados = montarDados(reserva, cliente, hab, tenant, delegacao);
        DocumentoConfig cfg = configDocumento(tenant);
        DocumentoConfig.Destino destinoCfg = (destino == Destino.MARINHA) ? cfg.marinha() : cfg.cliente();

        // Anti-furo: prévia NUNCA sai limpa — o documento sem carimbo só existe via
        // emissão (contabilizada). Com pendências carimba RASCUNHO; completo, PRÉVIA.
        String marcaDagua = !pendenciasDocumentacao(reserva.getTenantId(), hab, cliente, cfg).isEmpty()
            ? DocumentoPdfService.MARCA_RASCUNHO
            : DocumentoPdfService.MARCA_PREVIA;
        byte[] pdf = gerarParaDestino(dados, assinatura, cliente.getId(), hab, destinoCfg, marcaDagua).conteudo();

        // Metering: prévias contam como sinal antifraude (não cobrável)
        eventPublisher.publishEvent(new DocumentoPreviewGeradoEvent(
            reserva.getTenantId(), reservaId, destino.name(), java.time.Instant.now()));
        return pdf;
    }

    /**
     * Resolve o contexto delegado quando o plano da loja NÃO inclui emissão
     * própria (§8.K: portão comercial). Só se aplica ao caminho EMA (Marinha);
     * a emissão própria segue como sempre — inclusive para planos NULL (todos
     * os módulos), preservando o comportamento das lojas existentes.
     */
    private VinculoEmissaoService.DelegacaoContext resolverDelegacao(
            UUID tenantId, ReservaHabilitacao hab, boolean marinhaAplicavel) {
        if (!marinhaAplicavel
                || planoLimiteService.moduloHabilitado(tenantId, com.jetski.tenant.ModuloPlano.EMISSAO_PROPRIA)) {
            return null;
        }
        return vinculoEmissaoService.resolverParaEmissao(tenantId, hab.getInstrutorId());
    }

    /** Snapshot imutável do emissor gravado no documento (§3.4). */
    private String snapshotEmissor(VinculoEmissaoService.DelegacaoContext d) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("vinculoId", d.vinculoId());
            m.put("emissorTenantId", d.emissorTenantId());
            m.put("razaoSocial", d.razaoSocial());
            m.put("cnpj", d.cnpj());
            m.put("cidade", d.cidade());
            m.put("uf", d.uf());
            m.put("capitania", d.capitaniaCodigo());
            // Ofício à Capitania (V064): o reenvio assina pela EAMA como ela era na emissão.
            m.put("marinhaEmail", d.marinhaEmail());
            m.put("eamaRegistro", d.eamaRegistro());
            m.put("responsavelNome", d.responsavelNome());
            m.put("telefone", d.telefone());
            m.put("emailOficial", d.emailOficial());
            // V065: destino da notificação à EAMA. O worker reconstrói tudo do snapshot,
            // então sem isto a emissora não seria avisada no modo assíncrono.
            m.put("contatoEmail", d.contatoEmail());
            if (d.instrutorId() != null) {
                Map<String, Object> i = new LinkedHashMap<>();
                i.put("id", d.instrutorId());
                i.put("nome", d.instrutorNome());
                i.put("cpf", d.instrutorCpf());
                i.put("cha", d.instrutorCha());
                m.put("instrutor", i);
            }
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            log.warn("Snapshot do emissor não serializado (segue sem): {}", e.getMessage());
            return null;
        }
    }

    private DocumentoPdfService.DadosDocumento montarDados(Reserva reserva, Cliente cliente,
                                                          ReservaHabilitacao hab, Tenant tenant,
                                                          VinculoEmissaoService.DelegacaoContext delegacao) {
        String[] end = parseEndereco(cliente.getEnderecoJson());

        // Identidade da emissora no PDF: da EAMA parceira quando delegada (§8.J —
        // a operadora fica invisível no documento), senão do próprio tenant.
        String emissoraRazao = delegacao != null ? delegacao.razaoSocial() : tenant.getRazaoSocial();
        String emissoraCnpj = delegacao != null ? delegacao.cnpj() : tenant.getCnpj();
        String emissoraCidade = delegacao != null ? delegacao.cidade() : tenant.getCidade();

        String insNome, insRg, insOrgao, insCpf, insCha;
        java.time.LocalDate insData;
        byte[] instrutorAssinatura;
        if (delegacao != null) {
            insNome = delegacao.instrutorNome();
            insRg = delegacao.instrutorRg();
            insOrgao = delegacao.instrutorOrgaoEmissor();
            insCpf = delegacao.instrutorCpf();
            insCha = delegacao.instrutorCha();
            insData = delegacao.instrutorDataEmissao();
            instrutorAssinatura = lerAssinatura(delegacao.instrutorAssinaturaS3Key());
        } else {
            com.jetski.locacoes.domain.Instrutor instrutor = (hab.getInstrutorId() != null)
                ? instrutorRepository.findById(hab.getInstrutorId()).orElse(null)
                : null;
            insNome = instrutor != null ? instrutor.getNome() : null;
            insRg = instrutor != null ? instrutor.getRg() : null;
            insOrgao = instrutor != null ? instrutor.getOrgaoEmissor() : null;
            insCpf = instrutor != null ? instrutor.getCpf() : null;
            insCha = instrutor != null ? instrutor.getCha() : null;
            insData = instrutor != null ? instrutor.getDataEmissao() : null;
            instrutorAssinatura = instrutor != null ? lerAssinatura(instrutor.getAssinaturaS3Key()) : null;
        }
        String instrutorDataEmissao = insData != null
            ? String.format("%02d/%02d/%d", insData.getDayOfMonth(), insData.getMonthValue(), insData.getYear())
            : null;
        // Fronteira de apresentação: o documento é guardado canônico (V066), mas
        // os anexos da NORMAM são peça formal — sai pontuado, como se escreve.
        String documentoExibicao = com.jetski.locacoes.domain.Documentos.formatar(
            cliente.getDocumentoTipo(), cliente.getDocumento());

        return new DocumentoPdfService.DadosDocumento(
            cliente.getNome(), documentoExibicao, cliente.getRg(), cliente.getOrgaoEmissor(),
            cliente.getNacionalidade(), cliente.getNaturalidade(),
            cliente.getTelefone(), cliente.getWhatsapp(), cliente.getEmail(),
            end[0], end[1], end[2],
            emissoraRazao, emissoraCnpj,
            emissoraCidade, dataExtenso(), dataCurta(),
            hab.getVia() != null ? hab.getVia().name() : "EMA",
            Boolean.TRUE.equals(hab.getAnexoResidencia()),
            Boolean.TRUE.equals(hab.getUsaLentes()), Boolean.TRUE.equals(hab.getUsaAparelho()), true,
            insNome, insRg, insOrgao, insCpf, insCha,
            instrutorDataEmissao, instrutorAssinatura,
            hab.getGruNumero(), hab.getGruValor() != null ? hab.getGruValor().toPlainString() : null,
            Boolean.TRUE.equals(cliente.getEstrangeiro()));
    }

    private static final String[] MESES = {"janeiro", "fevereiro", "março", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"};

    private String dataCurta() {
        java.time.LocalDate hoje = java.time.LocalDate.now();
        return String.format("%02d/%02d/%d", hoje.getDayOfMonth(), hoje.getMonthValue(), hoje.getYear());
    }

    private String dataExtenso() {
        java.time.LocalDate hoje = java.time.LocalDate.now();
        return String.format("%d de %s de %d", hoje.getDayOfMonth(), MESES[hoje.getMonthValue() - 1], hoje.getYear());
    }

    /**
     * Lê os anexos do cliente p/ anexar ao PDF, filtrando por tipo conforme o
     * destino (identidade/comprovante/selfie configuráveis em separado). O anexo
     * de CHA acompanha o de identidade (ambos documentos pessoais do condutor).
     */
    private java.util.List<DocumentoPdfService.AnexoImagem> anexosDoCliente(
            UUID clienteId, DocumentoConfig.Destino cfg) {
        var lista = new java.util.ArrayList<>(clienteAnexoService.listar(clienteId));
        lista.sort(java.util.Comparator.comparingInt(a -> a.getTipo().ordinal()));
        var out = new java.util.ArrayList<DocumentoPdfService.AnexoImagem>();
        for (com.jetski.locacoes.domain.ClienteAnexo a : lista) {
            if (!incluiAnexo(a.getTipo(), cfg)) {
                continue;
            }
            try {
                out.add(new DocumentoPdfService.AnexoImagem(
                    tituloAnexo(a.getTipo()), clienteAnexoService.lerImagem(a)));
            } catch (Exception e) {
                log.warn("Anexo {} do cliente {} ilegível: {}", a.getTipo(), clienteId, e.getMessage());
            }
        }
        return out;
    }

    private static boolean incluiAnexo(com.jetski.locacoes.domain.ClienteAnexo.Tipo t,
            DocumentoConfig.Destino cfg) {
        return switch (t) {
            case IDENTIDADE, CHA -> cfg.anexoIdentidadeOn();
            case COMPROVANTE_RESIDENCIA -> cfg.anexoComprovanteOn();
            case SELFIE -> cfg.anexoSelfieOn();
        };
    }

    private static String tituloAnexo(com.jetski.locacoes.domain.ClienteAnexo.Tipo t) {
        return switch (t) {
            case IDENTIDADE -> "Documento de Identidade";
            case COMPROVANTE_RESIDENCIA -> "Comprovante de Residência";
            case SELFIE -> "Foto do Cliente";
            case CHA -> "Habilitação (CHA/CHV)";
        };
    }

    private String[] parseEndereco(String json) {
        if (json == null || json.isBlank()) {
            return new String[]{null, null, null};
        }
        try {
            JsonNode n = objectMapper.readTree(json);
            String logradouro = text(n, "logradouro");
            String numero = text(n, "numero");
            String complemento = text(n, "complemento");
            String bairro = text(n, "bairro");
            String cidade = text(n, "cidade");
            String uf = text(n, "uf");
            if (uf == null) uf = text(n, "estado");
            String cep = text(n, "cep");

            StringBuilder e = new StringBuilder();
            if (logradouro != null) e.append(logradouro);
            if (numero != null) e.append(", ").append(numero);
            if (complemento != null && !complemento.isBlank()) e.append(" ").append(complemento);
            if (bairro != null) e.append(" - ").append(bairro);

            String cidadeUf = (cidade != null ? cidade : "") + (uf != null ? "/" + uf : "");
            return new String[]{
                e.length() > 0 ? e.toString() : null,
                cidadeUf.isBlank() ? null : cidadeUf,
                cep
            };
        } catch (Exception ex) {
            log.debug("endereço não parseável como JSON: {}", ex.getMessage());
            return new String[]{null, null, null};
        }
    }

    private static String text(JsonNode n, String key) {
        return (n.hasNonNull(key) && !n.get(key).asText().isBlank()) ? n.get(key).asText() : null;
    }

    /**
     * Carrega a imagem da assinatura; tolerante a arquivo ausente no storage
     * (ex.: storage local efêmero apagado em recreate) — degrada para sem imagem
     * em vez de derrubar a emissão.
     */
    private byte[] lerAssinatura(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return storageService.getObject(key);
        } catch (Exception e) {
            log.warn("Assinatura ausente no storage (segue sem a imagem): key={}, erro={}", key, e.getMessage());
            return null;
        }
    }

    @Value
    @Builder
    public static class ResultadoReenvio {
        boolean enviadoMarinha;
        boolean enviadoCliente;
    }

    /**
     * Reenvia por e-mail um documento JÁ emitido (não regenera o PDF — lê do
     * storage). Útil quando o envio inicial falhou (ex.: SMTP não configurado).
     */
    @Transactional
    public ResultadoReenvio reenviarEmail(UUID documentoId) {
        // Mesmo caminho da emissão e do worker — o reenvio força os dois destinos,
        // ignorando o status gravado: o operador está afirmando que quer reenviar
        // (ex.: completou as pendências que haviam BLOQUEADO a Marinha).
        DocumentoEnvioService.EnvioContexto ctx = documentoEnvioService.carregar(documentoId, true);
        DocumentoEnvioService.ResultadoEnvio r = documentoEnvioService.despachar(ctx);
        documentoEnvioService.persistirStatus(documentoId, r);
        log.info("Reenvio de documento: docId={}, marinha={}, cliente={}",
            documentoId, r.marinha(), r.cliente());
        return ResultadoReenvio.builder()
            .enviadoMarinha(r.enviadoMarinha())
            .enviadoCliente(r.enviadoCliente())
            .build();
    }

    private String destinosJson(String marinha, String cliente) {
        try {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("marinha", marinha);
            m.put("cliente", cliente);
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Destinos que a emissão de fato endereça. Conta o que está a caminho (PENDENTE)
     * ou já saiu — no modo assíncrono nada foi enviado ainda quando o evento é
     * publicado, e a trilha não pode registrar "nenhum destino" por causa disso.
     */
    private String destinosResumo(EnvioStatus marinha, EnvioStatus cliente) {
        StringBuilder sb = new StringBuilder();
        if (enderecado(marinha)) sb.append("marinha");
        if (enderecado(cliente)) sb.append(sb.length() > 0 ? ",cliente" : "cliente");
        return sb.toString();
    }

    private static boolean enderecado(EnvioStatus s) {
        return s == EnvioStatus.PENDENTE || s == EnvioStatus.ENVIADO;
    }

}
