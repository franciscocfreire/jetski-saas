package com.jetski.locacoes.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.locacoes.api.dto.DocumentoEnvioStatusResponse;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.DocumentoEmitido;
import com.jetski.locacoes.domain.EnvioStatus;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.DocumentoEmitidoRepository;
import com.jetski.locacoes.internal.repository.ReservaHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.email.EmailService;
import com.jetski.shared.storage.StorageService;
import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.DocumentoConfig;
import com.jetski.tenant.domain.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Envio por e-mail de um documento JÁ emitido — ofício à Capitania e via do cliente.
 *
 * <p>Existe para que os três caminhos que enviam (emissão síncrona, worker assíncrono
 * e reenvio manual) compartilhem exatamente o mesmo código. Antes a emissão tinha uma
 * cópia e o reenvio outra, e elas já haviam divergido (ver {@code hab == null} e o hash
 * carregado no ofício).
 *
 * <p><b>As três fronteiras transacionais são deliberadas</b> e a razão de ser desta
 * classe: {@link #carregar} e {@link #persistirStatus} são transações curtas, enquanto
 * {@link #despachar} — onde moram os ~25 s de SMTP — roda <b>sem</b> transação. Anotar
 * o worker inteiro com {@code @Transactional} recriaria, num pool diferente, o problema
 * que motivou este trabalho: segurar uma conexão de banco durante o envio.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentoEnvioService {

    private final ReservaRepository reservaRepository;
    private final ClienteRepository clienteRepository;
    private final ReservaHabilitacaoRepository habilitacaoRepository;
    private final DocumentoEmitidoRepository documentoRepository;
    private final StorageService storageService;
    private final EmailService emailService;
    private final TenantQueryService tenantQueryService;
    private final ClienteAnexoService clienteAnexoService;
    private final ObjectMapper objectMapper;

    /** Limite da mensagem de erro guardada no banco — stack traces de SMTP são longos. */
    private static final int ERRO_MAX = 500;

    /**
     * Tudo o que o envio precisa, já resolvido e com os PDFs em memória. Autocontido
     * de propósito: {@link #despachar} não toca no banco.
     */
    public record EnvioContexto(
        UUID documentoId,
        UUID tenantId,
        UUID reservaId,
        String marinhaEmail,
        String clienteEmail,
        String emissorContatoEmail,
        MarinhaEmailTemplate.DadosOficio oficio,
        String assuntoCliente,
        String corpoCliente,
        String nomeArquivo,
        String assuntoEmissor,
        String corpoEmissor,
        byte[] pdfCliente,
        byte[] pdfMarinha,
        EnvioStatus marinhaInicial,
        EnvioStatus clienteInicial
    ) {}

    /** Desfecho do envio, por destino. */
    public record ResultadoEnvio(
        EnvioStatus marinha, String marinhaErro,
        EnvioStatus cliente, String clienteErro
    ) {
        public boolean enviadoMarinha() {
            return marinha == EnvioStatus.ENVIADO;
        }

        public boolean enviadoCliente() {
            return cliente == EnvioStatus.ENVIADO;
        }
    }

    // ------------------------------------------------------------------
    // 1) Carregar — transação curta e read-only
    // ------------------------------------------------------------------

    /**
     * Monta o contexto do envio a partir do que está persistido: PDFs no storage,
     * emissor no snapshot do documento (não no vínculo atual — a EAMA pode ter mudado).
     *
     * @param reenvio {@code true} = reenvio manual: ignora os status gravados e tenta os
     *                dois destinos de novo (o operador está afirmando que quer reenviar).
     *                {@code false} = worker: só envia o que está {@link EnvioStatus#PENDENTE}.
     */
    @Transactional(readOnly = true)
    public EnvioContexto carregar(UUID documentoId, boolean reenvio) {
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
        ReservaHabilitacao hab = habilitacaoRepository.findByReservaId(reserva.getId()).orElse(null);

        byte[] pdfCliente = storageService.getObject(doc.getS3Key());

        // CHA não tem documentação à Marinha; EMA tem (PDF específico, ou o canônico
        // como fallback para emissões anteriores a essa separação).
        boolean marinhaAplicavel = hab == null || hab.getVia() == ReservaHabilitacao.Via.EMA;
        byte[] pdfMarinha = null;
        if (marinhaAplicavel) {
            try {
                pdfMarinha = storageService.getObject(keyMarinhaDe(doc.getS3Key()));
            } catch (Exception e) {
                pdfMarinha = pdfCliente; // legado: emissão sem PDF da Marinha separado
            }
        }

        // Delegada: o ofício e o destino são os da EAMA emissora como registrados na
        // emissão. Própria: o tenant.
        Emissor emissor = Emissor.fromSnapshot(doc.getEmissorSnapshot(), tenant, objectMapper);

        EnvioStatus marinhaInicial = reenvio
            ? (pdfMarinha != null ? EnvioStatus.PENDENTE : EnvioStatus.NAO_APLICAVEL)
            : ouPendente(doc.getMarinhaEnvioStatus());
        EnvioStatus clienteInicial = reenvio ? EnvioStatus.PENDENTE : ouPendente(doc.getClienteEnvioStatus());

        // O hash no ofício descreve o PDF ANEXADO — por isso o da via da Marinha,
        // e não o hash canônico (do PDF do cliente) gravado no documento.
        String hashMarinha = pdfMarinha != null ? sha256Hex(pdfMarinha) : doc.getHashSha256();

        return new EnvioContexto(
            doc.getId(), reserva.getTenantId(), reserva.getId(),
            emissor.marinhaEmail(), cliente.getEmail(),
            // A EAMA emissora só é avisada da emissão original; um reenvio não a notifica.
            reenvio ? null : emissor.contatoEmail(),
            oficio(emissor, cliente, hab, reserva.getId(), configDocumento(tenant), hashMarinha, reenvio),
            "Seus documentos — " + tenant.getRazaoSocial(),
            corpoCliente(cliente, hab),
            DocumentoNome.de(cliente.getNome(), cliente.getDocumento()),
            "Documento emitido em seu nome — " + tenant.getRazaoSocial(),
            corpoNotificacaoEmissor(tenant, cliente, hab, doc.getHashSha256()),
            pdfCliente, pdfMarinha,
            marinhaInicial, clienteInicial);
    }

    /**
     * Contexto montado com o que a emissão já tem em memória — sem reler storage nem
     * banco. O modo síncrono usa este caminho: reler pelo id exigiria que o id já
     * estivesse atribuído e custaria dois downloads de ~1 MB à toa.
     */
    public EnvioContexto contextoDaEmissao(
            UUID documentoId, Reserva reserva, Tenant tenant, Cliente cliente,
            ReservaHabilitacao hab, DocumentoConfig cfg,
            VinculoEmissaoService.DelegacaoContext delegacao,
            byte[] pdfCliente, String hashCliente, byte[] pdfMarinha,
            EnvioStatus marinhaInicial, EnvioStatus clienteInicial) {
        Emissor emissor = Emissor.of(tenant, delegacao);
        // O hash no ofício descreve o PDF ANEXADO (a via da Marinha), não o canônico.
        String hashMarinha = pdfMarinha != null ? sha256Hex(pdfMarinha) : hashCliente;
        return new EnvioContexto(
            documentoId, reserva.getTenantId(), reserva.getId(),
            emissor.marinhaEmail(), cliente.getEmail(), emissor.contatoEmail(),
            oficio(emissor, cliente, hab, reserva.getId(), cfg, hashMarinha, false),
            "Seus documentos — " + tenant.getRazaoSocial(),
            corpoCliente(cliente, hab),
            DocumentoNome.de(cliente.getNome(), cliente.getDocumento()),
            "Documento emitido em seu nome — " + tenant.getRazaoSocial(),
            corpoNotificacaoEmissor(tenant, cliente, hab, hashCliente),
            pdfCliente, pdfMarinha,
            marinhaInicial, clienteInicial);
    }

    /** Status ausente (documento anterior à V065) é tratado como "a enviar". */
    private static EnvioStatus ouPendente(EnvioStatus s) {
        return s == null ? EnvioStatus.PENDENTE : s;
    }

    // ------------------------------------------------------------------
    // 2) Despachar — SEM transação: é aqui que o SMTP demora
    // ------------------------------------------------------------------

    /**
     * Envia o que estiver {@link EnvioStatus#PENDENTE} no contexto. Best-effort: uma
     * falha de SMTP nunca derruba o fluxo, só vira status {@link EnvioStatus#FALHOU}
     * com o motivo — o PDF continua disponível para download e reenvio.
     */
    public ResultadoEnvio despachar(EnvioContexto ctx) {
        EnvioStatus marinha = ctx.marinhaInicial();
        String marinhaErro = null;
        if (marinha == EnvioStatus.PENDENTE) {
            if (vazio(ctx.marinhaEmail()) || ctx.pdfMarinha() == null) {
                marinha = EnvioStatus.SEM_DESTINATARIO;
            } else {
                String subject = MarinhaEmailTemplate.assunto(ctx.oficio());
                try {
                    emailService.sendEmailComAnexo(ctx.marinhaEmail(), subject,
                        MarinhaEmailTemplate.corpoHtml(ctx.oficio()),
                        MarinhaEmailTemplate.nomeArquivo(ctx.oficio()),
                        ctx.pdfMarinha(), "application/pdf", ctx.oficio().emailOficial());
                    marinha = EnvioStatus.ENVIADO;
                } catch (Exception e) {
                    log.warn("Falha ao enviar e-mail à Marinha (segue sem enviar): to={}, subject={}, erro={}",
                        ctx.marinhaEmail(), subject, e.getMessage());
                    marinha = EnvioStatus.FALHOU;
                    marinhaErro = motivo(e);
                }
            }
        }

        EnvioStatus destinatario = ctx.clienteInicial();
        String clienteErro = null;
        if (destinatario == EnvioStatus.PENDENTE) {
            if (vazio(ctx.clienteEmail())) {
                destinatario = EnvioStatus.SEM_DESTINATARIO;
            } else {
                try {
                    emailService.sendEmailComAnexo(ctx.clienteEmail(), ctx.assuntoCliente(),
                        ctx.corpoCliente(), ctx.nomeArquivo(), ctx.pdfCliente(), "application/pdf");
                    destinatario = EnvioStatus.ENVIADO;
                } catch (Exception e) {
                    log.warn("Falha ao enviar e-mail (segue sem enviar): to={}, subject={}, erro={}",
                        ctx.clienteEmail(), ctx.assuntoCliente(), e.getMessage());
                    destinatario = EnvioStatus.FALHOU;
                    clienteErro = motivo(e);
                }
            }
        }

        // Notificação à EAMA emissora (delegada): o documento saiu em nome dela.
        // Best-effort e sem status próprio — o registro dela é o painel de emissões.
        if (!vazio(ctx.emissorContatoEmail())) {
            try {
                emailService.sendEmailComAnexo(ctx.emissorContatoEmail(),
                    ctx.assuntoEmissor(), ctx.corpoEmissor(), ctx.nomeArquivo(),
                    ctx.pdfMarinha() != null ? ctx.pdfMarinha() : ctx.pdfCliente(), "application/pdf");
            } catch (Exception e) {
                log.warn("Falha ao notificar a EAMA emissora (segue sem enviar): to={}, erro={}",
                    ctx.emissorContatoEmail(), e.getMessage());
            }
        }

        return new ResultadoEnvio(marinha, marinhaErro, destinatario, clienteErro);
    }

    // ------------------------------------------------------------------
    // 3) Persistir — transação curta e própria
    // ------------------------------------------------------------------

    /**
     * Grava o desfecho. Único escritor do par (status, timestamp) — é o que garante
     * a invariante {@code ENVIADO <=> *_enviado_em != null} sem CHECK no banco.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistirStatus(UUID documentoId, ResultadoEnvio r) {
        DocumentoEmitido doc = documentoRepository.findById(documentoId)
            .orElseThrow(() -> new NotFoundException("Documento não encontrado: " + documentoId));
        aplicar(doc, r);
        documentoRepository.save(doc);
    }

    /** Idem, para quando já existe uma transação e a entidade está gerenciada. */
    public void aplicar(DocumentoEmitido doc, ResultadoEnvio r) {
        doc.setMarinhaEnvioStatus(r.marinha());
        doc.setMarinhaEnvioErro(r.marinhaErro());
        doc.setClienteEnvioStatus(r.cliente());
        doc.setClienteEnvioErro(r.clienteErro());
        if (r.enviadoMarinha()) doc.setMarinhaEnviadoEm(Instant.now());
        if (r.enviadoCliente()) doc.setClienteEnviadoEm(Instant.now());
        doc.setEnvioAtualizadoEm(Instant.now());
    }

    /** O worker morreu antes de gravar: o documento não pode ficar eternamente PENDENTE. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarFalha(UUID documentoId, Exception causa) {
        documentoRepository.findById(documentoId).ifPresent(doc -> {
            if (doc.getMarinhaEnvioStatus() == EnvioStatus.PENDENTE) {
                doc.setMarinhaEnvioStatus(EnvioStatus.FALHOU);
                doc.setMarinhaEnvioErro(motivo(causa));
            }
            if (doc.getClienteEnvioStatus() == EnvioStatus.PENDENTE) {
                doc.setClienteEnvioStatus(EnvioStatus.FALHOU);
                doc.setClienteEnvioErro(motivo(causa));
            }
            doc.setEnvioAtualizadoEm(Instant.now());
            documentoRepository.save(doc);
        });
    }

    /** Estado atual do envio — leitura barata (por PK), serve a polling de 2 em 2 s. */
    @Transactional(readOnly = true)
    public DocumentoEnvioStatusResponse statusEnvio(UUID documentoId) {
        DocumentoEmitido doc = documentoRepository.findById(documentoId)
            .orElseThrow(() -> new NotFoundException("Documento não encontrado: " + documentoId));
        EnvioStatus marinha = ouPendente(doc.getMarinhaEnvioStatus());
        EnvioStatus cliente = ouPendente(doc.getClienteEnvioStatus());
        return new DocumentoEnvioStatusResponse(
            doc.getId(),
            new DocumentoEnvioStatusResponse.Destino(marinha, doc.getMarinhaEnviadoEm(), doc.getMarinhaEnvioErro()),
            new DocumentoEnvioStatusResponse.Destino(cliente, doc.getClienteEnviadoEm(), doc.getClienteEnvioErro()),
            marinha.terminal() && cliente.terminal(),
            doc.getEnvioAtualizadoEm());
    }

    // ------------------------------------------------------------------
    // Helpers compartilhados com o EmissaoService
    // ------------------------------------------------------------------

    /**
     * Key do PDF no recorte da Marinha, derivada da key da via do cliente (separadas
     * desde a V039). Derivar em vez de montar pelo par (tenant, reserva) é o que
     * permite versionar a key numa reemissão sem perder o par: a legada
     * {@code documento.pdf} continua virando {@code documento-marinha.pdf}, e uma
     * reemissão {@code documento-123.pdf} vira {@code documento-123-marinha.pdf}.
     */
    static String keyMarinhaDe(String s3KeyCliente) {
        return s3KeyCliente.endsWith(".pdf")
            ? s3KeyCliente.substring(0, s3KeyCliente.length() - 4) + "-marinha.pdf"
            : s3KeyCliente + "-marinha.pdf";
    }

    static DocumentoConfig configDocumento(Tenant tenant) {
        DocumentoConfig c = tenant.getDocumentoConfig();
        return (c != null ? c : DocumentoConfig.padrao()).comDefaults();
    }

    static String sha256Hex(byte[] data) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Monta o ofício (NORMAM-212 5.4.2) com a lista dos documentos realmente incluídos no PDF. */
    MarinhaEmailTemplate.DadosOficio oficio(Emissor e, Cliente c, ReservaHabilitacao hab,
            UUID reservaId, DocumentoConfig cfg, String hash, boolean reenvio) {
        return new MarinhaEmailTemplate.DadosOficio(
            e.nome(), e.cnpj(), e.registro(), e.responsavel(), e.telefone(), e.emailOficial(),
            c.getNome(), c.getDocumento(), c.getDocumentoTipo(), Boolean.TRUE.equals(c.getEstrangeiro()),
            hab != null ? hab.getGruNumero() : null, reservaId,
            anexosOficio(cfg.marinha(), c, hab), hash, reenvio);
    }

    /** Itens do 5.4.2-a presentes no PDF da Marinha, conforme o recorte do tenant e o que o cliente entregou. */
    private List<String> anexosOficio(DocumentoConfig.Destino d, Cliente c, ReservaHabilitacao hab) {
        List<String> a = new java.util.ArrayList<>();
        if (d.saudeOn()) a.add("Autodeclaração de Atestado de Saúde – Anexo 5-C");
        if (d.instrutorOn()) {
            a.add(Boolean.TRUE.equals(c.getEstrangeiro())
                ? "Atestado de Demonstração – Anexo 5-B (5-B-1/5-B-2 e versões em inglês 5-B-3/5-B-4)"
                : "Atestado de Demonstração – Anexo 5-B (5-B-1 e 5-B-2)");
        }
        if (d.residenciaOn() && hab != null && Boolean.TRUE.equals(hab.getAnexoResidencia())) {
            a.add("Declaração de Residência – Anexo 1-C");
        }
        if (d.anexoComprovanteOn() && anexoPresente(c.getId(),
                com.jetski.locacoes.domain.ClienteAnexo.Tipo.COMPROVANTE_RESIDENCIA)) {
            a.add("Comprovante de residência");
        }
        if (d.anexoIdentidadeOn() && anexoPresente(c.getId(),
                com.jetski.locacoes.domain.ClienteAnexo.Tipo.IDENTIDADE)) {
            a.add("Documento oficial de identificação, com fotografia");
        }
        if (d.anexoSelfieOn() && anexoPresente(c.getId(),
                com.jetski.locacoes.domain.ClienteAnexo.Tipo.SELFIE)) {
            a.add("Fotografia do locatário");
        }
        if (d.comprovanteGruOn() && hab != null && hab.getGruComprovanteS3Key() != null) {
            a.add("Comprovante de pagamento da GRU");
        }
        return a;
    }

    String corpoCliente(Cliente c, ReservaHabilitacao hab) {
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

    String corpoNotificacaoEmissor(Tenant operadora, Cliente c, ReservaHabilitacao hab, String hash) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p>A operadora parceira <b>").append(safe(operadora.getRazaoSocial()))
          .append("</b> emitiu documentação NORMAM-212 <b>em nome da sua EAMA</b>.</p>");
        sb.append("<p>Condutor: <b>").append(safe(c.getNome())).append("</b> (CPF ")
          .append(safe(c.getDocumento())).append(")</p>");
        if (hab != null && hab.getGruNumero() != null && !hab.getGruNumero().isBlank()) {
            sb.append("<p>GRU: <b>").append(safe(hab.getGruNumero())).append("</b></p>");
        }
        sb.append("<p>Hash SHA-256: <code>").append(safe(hash)).append("</code></p>");
        sb.append("<p>O registro completo está no painel Emissões delegadas do seu backoffice, ")
          .append("onde você pode reenviar o PDF à Capitania ou bloquear a parceria.</p>");
        return sb.toString();
    }

    private boolean anexoPresente(UUID clienteId, com.jetski.locacoes.domain.ClienteAnexo.Tipo tipo) {
        try {
            return clienteAnexoService.buscar(clienteId, tipo).isPresent();
        } catch (Exception e) {
            log.warn("Falha ao checar anexo {} do cliente {}: {}", tipo, clienteId, e.getMessage());
            return false;
        }
    }

    private static String motivo(Exception e) {
        String m = e.getMessage() != null ? e.getMessage() : e.toString();
        // A causa raiz é o que interessa ("Read timed out"), não o wrapper do Spring.
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) raiz = raiz.getCause();
        if (raiz != e && raiz.getMessage() != null) m = m + " — " + raiz.getMessage();
        byte[] bytes = m.getBytes(StandardCharsets.UTF_8);
        return bytes.length <= ERRO_MAX ? m : new String(bytes, 0, ERRO_MAX, StandardCharsets.UTF_8);
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    /**
     * Quem assina o ofício à Capitania: o próprio tenant (emissão própria) ou a EAMA
     * emissora (delegada) — na emissão pelo {@code DelegacaoContext}, no reenvio/worker
     * pelo {@code emissor_snapshot} do documento (campos ausentes caem no tenant).
     */
    record Emissor(String nome, String cnpj, String registro, String responsavel, String telefone,
                   String emailOficial, String marinhaEmail, String contatoEmail) {

        static Emissor of(Tenant t, VinculoEmissaoService.DelegacaoContext d) {
            if (d != null) {
                return new Emissor(d.razaoSocial(), d.cnpj(), d.eamaRegistro(), d.responsavelNome(),
                    d.telefone(), d.emailOficial(), d.marinhaEmail(), d.contatoEmail());
            }
            return new Emissor(t.getRazaoSocial(), t.getCnpj(), t.getEamaRegistro(), t.getResponsavelNome(),
                t.getTelefone(), t.getEmailOficial(), t.getMarinhaEmail(), null);
        }

        @SuppressWarnings("unchecked")
        static Emissor fromSnapshot(String snapshotJson, Tenant t, ObjectMapper om) {
            Emissor proprio = of(t, null);
            if (snapshotJson == null || snapshotJson.isBlank()) return proprio;
            try {
                Map<String, Object> m = om.readValue(snapshotJson, Map.class);
                return new Emissor(
                    str(m, "razaoSocial", proprio.nome()), str(m, "cnpj", proprio.cnpj()),
                    str(m, "eamaRegistro", null), str(m, "responsavelNome", null),
                    str(m, "telefone", null), str(m, "emailOficial", null),
                    // snapshots antigos (antes da V064) não têm o e-mail: cai no tenant
                    str(m, "marinhaEmail", proprio.marinhaEmail()),
                    // snapshots anteriores à V065 não têm o contato: a EAMA não é notificada
                    str(m, "contatoEmail", null));
            } catch (Exception e) {
                return proprio;
            }
        }

        private static String str(Map<String, Object> m, String k, String fallback) {
            Object v = m.get(k);
            return v instanceof String s && !s.isBlank() ? s : fallback;
        }
    }
}
