package com.jetski.locacoes.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.DocumentoEmitido;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaAceite;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.DocumentoEmitidoRepository;
import com.jetski.locacoes.internal.repository.ReservaAceiteRepository;
import com.jetski.locacoes.internal.repository.ReservaHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.reservas.domain.event.DocumentosEmitidosEvent;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.email.EmailService;
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
    private final ClienteRepository clienteRepository;
    private final com.jetski.locacoes.internal.repository.InstrutorRepository instrutorRepository;
    private final ReservaHabilitacaoRepository habilitacaoRepository;
    private final ReservaAceiteRepository aceiteRepository;
    private final DocumentoEmitidoRepository documentoRepository;
    private final StorageService storageService;
    private final EmailService emailService;
    private final TenantQueryService tenantQueryService;
    private final DocumentoPdfService documentoPdfService;
    private final ClienteAnexoService clienteAnexoService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final com.jetski.shared.assinatura.CarimboTempoService carimboTempoService;
    private final PadesSignatureService padesSignatureService;

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
        boolean docCompleta;
        java.util.List<String> pendencias;
    }

    @Transactional
    public ResultadoEmissao emitir(UUID reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + reservaId));

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

        DocumentoPdfService.DadosDocumento dados = montarDados(reserva, cliente, hab, tenant);
        DocumentoConfig cfg = configDocumento(tenant);

        // CHA = cliente já habilitado: não há documentação NORMAM nem envio à Marinha.
        boolean marinhaAplicavel = hab.getVia() == ReservaHabilitacao.Via.EMA;

        // PDFs por destino: a Marinha pode receber um recorte diferente do cliente
        // (ex.: sem o Termo de Responsabilidade), conforme a parametrização do tenant.
        DocumentoPdfService.DocumentoPdf pdfCliente =
            gerarParaDestino(dados, assinatura, cliente.getId(), hab, cfg.cliente(), false);
        DocumentoPdfService.DocumentoPdf pdfMarinha = marinhaAplicavel
            ? gerarParaDestino(dados, assinatura, cliente.getId(), hab, cfg.marinha(), false)
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
        String key = String.format("%s/reserva/%s/documento.pdf", reserva.getTenantId(), reservaId);
        storageService.putObject(key, pdfCliente.conteudo(), "application/pdf");
        if (pdfMarinha != null) {
            storageService.putObject(keyMarinha(reserva.getTenantId(), reservaId),
                pdfMarinha.conteudo(), "application/pdf");
        }

        String marinhaEmail = tenant.getMarinhaEmail();
        String clienteEmail = cliente.getEmail();

        DocumentoEmitido doc = documentoRepository.save(DocumentoEmitido.builder()
            .tenantId(reserva.getTenantId())
            .reservaId(reservaId)
            .s3Key(key)
            .hashSha256(pdfCliente.sha256())
            .destinos(destinosJson(marinhaEmail, clienteEmail))
            .emitidoEm(Instant.now())
            .build());

        // Documentação completa? Só com tudo cumprido a Marinha pode receber o e-mail.
        java.util.List<String> pendencias = pendenciasDocumentacao(hab, cliente, cfg.obrigatoriosMarinha());
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

        // E-mail à Marinha só quando aplicável (EMA) e a documentação está completa.
        boolean enviadoMarinha = marinhaAplicavel && docCompleta && enviar(marinhaEmail,
            "Documentos NORMAM-212 — reserva " + reservaId, corpoMarinha(cliente), pdfMarinha.conteudo());
        boolean enviadoCliente = enviar(clienteEmail,
            "Seus documentos — " + tenant.getRazaoSocial(), corpoCliente(cliente, hab), pdfCliente.conteudo());
        if (!docCompleta) {
            log.info("Marinha NÃO notificada (reserva {}): pendências {}", reservaId, pendencias);
        }

        eventPublisher.publishEvent(DocumentosEmitidosEvent.of(
            reserva.getTenantId(), reservaId, doc.getId(),
            destinosResumo(enviadoMarinha, enviadoCliente), TenantContext.getUsuarioId()));

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
            .docCompleta(docCompleta)
            .pendencias(pendencias)
            .build();
    }

    /**
     * Itens exigidos p/ liberar a Marinha. A habilitação resolvida (CHA/GRU) é
     * sempre exigida; os demais itens (EMA) são parametrizados por tenant em
     * {@link DocumentoConfig.ObrigatoriosMarinha}.
     */
    private java.util.List<String> pendenciasDocumentacao(ReservaHabilitacao hab, Cliente cliente,
            DocumentoConfig.ObrigatoriosMarinha obr) {
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
                Boolean.TRUE.equals(hab.getAnexoRegras()), hab.getVideoaulaEm() != null,
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
        DocumentoConfig c = tenant.getDocumentoConfig();
        return (c != null ? c : DocumentoConfig.padrao()).comDefaults();
    }

    private String keyMarinha(UUID tenantId, UUID reservaId) {
        return String.format("%s/reserva/%s/documento-marinha.pdf", tenantId, reservaId);
    }

    /**
     * Monta o PDF para um destino, aplicando o recorte de seções configurado
     * (1-C/5-C/5-B/Termo internos + anexos do cliente + comprovante da GRU).
     */
    private DocumentoPdfService.DocumentoPdf gerarParaDestino(
            DocumentoPdfService.DadosDocumento dados, byte[] assinatura, UUID clienteId,
            ReservaHabilitacao hab, DocumentoConfig.Destino cfg, boolean rascunho) {
        java.util.Set<DocumentoPdfService.Secao> secoes =
            java.util.EnumSet.noneOf(DocumentoPdfService.Secao.class);
        if (cfg.residenciaOn()) secoes.add(DocumentoPdfService.Secao.RESIDENCIA);
        if (cfg.saudeOn()) secoes.add(DocumentoPdfService.Secao.SAUDE);
        if (cfg.instrutorOn()) secoes.add(DocumentoPdfService.Secao.INSTRUTOR);
        if (cfg.termoOn()) secoes.add(DocumentoPdfService.Secao.TERMO);

        java.util.List<DocumentoPdfService.AnexoImagem> anexos = anexosDoCliente(clienteId, cfg);

        DocumentoPdfService.DocumentoPdf pdf =
            documentoPdfService.gerarDocumentoConsolidado(dados, assinatura, anexos, secoes, rascunho);

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

        DocumentoPdfService.DadosDocumento dados = montarDados(reserva, cliente, hab, tenant);
        DocumentoConfig cfg = configDocumento(tenant);
        DocumentoConfig.Destino destinoCfg = (destino == Destino.MARINHA) ? cfg.marinha() : cfg.cliente();

        boolean rascunho = !pendenciasDocumentacao(hab, cliente, cfg.obrigatoriosMarinha()).isEmpty();
        return gerarParaDestino(dados, assinatura, cliente.getId(), hab, destinoCfg, rascunho).conteudo();
    }

    private DocumentoPdfService.DadosDocumento montarDados(Reserva reserva, Cliente cliente,
                                                          ReservaHabilitacao hab, Tenant tenant) {
        String[] end = parseEndereco(cliente.getEnderecoJson());
        com.jetski.locacoes.domain.Instrutor instrutor = (hab.getInstrutorId() != null)
            ? instrutorRepository.findById(hab.getInstrutorId()).orElse(null)
            : null;
        byte[] instrutorAssinatura = (instrutor != null)
            ? lerAssinatura(instrutor.getAssinaturaS3Key())
            : null;
        String instrutorDataEmissao = (instrutor != null && instrutor.getDataEmissao() != null)
            ? String.format("%02d/%02d/%d", instrutor.getDataEmissao().getDayOfMonth(),
                instrutor.getDataEmissao().getMonthValue(), instrutor.getDataEmissao().getYear())
            : null;
        return new DocumentoPdfService.DadosDocumento(
            cliente.getNome(), cliente.getDocumento(), cliente.getRg(), cliente.getOrgaoEmissor(),
            cliente.getNacionalidade(), cliente.getNaturalidade(),
            cliente.getTelefone(), cliente.getWhatsapp(), cliente.getEmail(),
            end[0], end[1], end[2],
            tenant.getRazaoSocial(), tenant.getCnpj(),
            tenant.getCidade(), dataExtenso(), dataCurta(),
            hab.getVia() != null ? hab.getVia().name() : "EMA",
            Boolean.TRUE.equals(hab.getAnexoResidencia()),
            Boolean.TRUE.equals(hab.getUsaLentes()), Boolean.TRUE.equals(hab.getUsaAparelho()), true,
            instrutor != null ? instrutor.getNome() : null,
            instrutor != null ? instrutor.getRg() : null,
            instrutor != null ? instrutor.getOrgaoEmissor() : null,
            instrutor != null ? instrutor.getCpf() : null,
            instrutor != null ? instrutor.getCha() : null,
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
    @Transactional(readOnly = true)
    public ResultadoReenvio reenviarEmail(UUID documentoId) {
        DocumentoEmitido doc = documentoRepository.findById(documentoId)
            .orElseThrow(() -> new NotFoundException("Documento não encontrado: " + documentoId));
        Reserva reserva = reservaRepository.findById(doc.getReservaId())
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + doc.getReservaId()));
        Cliente cliente = clienteRepository.findById(reserva.getClienteId())
            .orElseThrow(() -> new NotFoundException("Cliente não encontrado: " + reserva.getClienteId()));
        Tenant tenant = tenantQueryService.findById(reserva.getTenantId());
        if (tenant == null) {
            throw new NotFoundException("Tenant não encontrado: " + reserva.getTenantId());
        }

        byte[] pdfCliente = storageService.getObject(doc.getS3Key());

        // CHA não tem documentação à Marinha; EMA tem (PDF específico, ou o canônico
        // como fallback para emissões anteriores a essa separação).
        ReservaHabilitacao hab = habilitacaoRepository.findByReservaId(reserva.getId()).orElse(null);
        boolean marinhaAplicavel = hab == null || hab.getVia() == ReservaHabilitacao.Via.EMA;
        byte[] pdfMarinha = null;
        if (marinhaAplicavel) {
            try {
                pdfMarinha = storageService.getObject(keyMarinha(reserva.getTenantId(), reserva.getId()));
            } catch (Exception e) {
                pdfMarinha = pdfCliente; // legado: emissão sem PDF da Marinha separado
            }
        }
        boolean enviadoMarinha = pdfMarinha != null && enviar(tenant.getMarinhaEmail(),
            "Documentos NORMAM-212 — reserva " + reserva.getId(), corpoMarinha(cliente), pdfMarinha);
        boolean enviadoCliente = enviar(cliente.getEmail(),
            "Seus documentos — " + tenant.getRazaoSocial(), corpoCliente(cliente, hab), pdfCliente);
        log.info("Reenvio de documento: docId={}, marinha={}, cliente={}",
            documentoId, enviadoMarinha, enviadoCliente);
        return ResultadoReenvio.builder()
            .enviadoMarinha(enviadoMarinha)
            .enviadoCliente(enviadoCliente)
            .build();
    }

    private boolean enviar(String to, String subject, String htmlBody, byte[] pdf) {
        if (to == null || to.isBlank()) {
            return false;
        }
        // Best-effort: o documento já foi gerado e salvo no storage. Uma falha de
        // SMTP (ex.: credenciais ausentes) NÃO deve derrubar a emissão — apenas
        // registra que o e-mail não foi entregue (o PDF fica disponível p/ download).
        try {
            emailService.sendEmailComAnexo(to, subject, htmlBody, "documentos.pdf", pdf, "application/pdf");
            return true;
        } catch (Exception e) {
            log.warn("Falha ao enviar e-mail (segue sem enviar): to={}, subject={}, erro={}", to, subject, e.getMessage());
            return false;
        }
    }

    private String corpoMarinha(Cliente c) {
        return "<p>Segue em anexo a documentação (NORMAM-212/DPC) referente ao locatário "
            + "<b>" + safe(c.getNome()) + "</b> (CPF " + safe(c.getDocumento()) + ").</p>";
    }

    private String corpoCliente(Cliente c, ReservaHabilitacao hab) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Olá ").append(safe(c.getNome())).append(",</p>");
        sb.append("<p>Seguem em anexo seus documentos do passeio.</p>");
        // GRU (habilitação temporária EMA): informa o número no corpo do e-mail.
        if (hab != null && hab.getGruNumero() != null && !hab.getGruNumero().isBlank()) {
            sb.append("<p>GRU (taxa CHA-MTA-E): <b>").append(safe(hab.getGruNumero())).append("</b>");
            if (hab.getGruValor() != null) {
                sb.append(" — Valor: R$ ").append(hab.getGruValor().toPlainString());
            }
            sb.append("</p>");
        }
        return sb.toString();
        // O link de ativação da conta (claim, F2.7) é enviado separadamente,
        // como passo próprio do balcão (POST /clientes/{id}/claim).
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

    private String destinosResumo(boolean marinha, boolean cliente) {
        StringBuilder sb = new StringBuilder();
        if (marinha) sb.append("marinha");
        if (cliente) sb.append(sb.length() > 0 ? ",cliente" : "cliente");
        return sb.toString();
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }
}
