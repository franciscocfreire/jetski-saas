package com.jetski.locacoes.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.event.GruEmitidaEvent;
import com.jetski.locacoes.internal.gru.GruBoletoResultado;
import com.jetski.locacoes.internal.gru.GruClient;
import com.jetski.locacoes.internal.gru.GruContribuinte;
import com.jetski.locacoes.internal.gru.GruException;
import com.jetski.locacoes.internal.gru.GruPagamentoStatus;
import com.jetski.locacoes.internal.gru.GruResultado;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.ReservaHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.email.EmailService;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.storage.StorageService;
import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Geração da GRU (taxa da Marinha) + PIX para a habilitação EMA de uma reserva.
 *
 * <p>Best-effort: nunca lança {@link GruException} para fora — em falha devolve
 * {@link GruGeracao} com {@code sucesso=false} + código, para o backoffice cair
 * no <b>fluxo manual</b> (operador digita número/valor). Cada GRU é uma obrigação
 * de pagamento, então uma GRU válida e não vencida é <b>reaproveitada</b> (não
 * gera duplicada).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GruService {

    private final ReservaHabilitacaoRepository habilitacaoRepository;
    private final ClienteNotificacaoService clienteNotificacaoService;
    private final ReservaRepository reservaRepository;
    private final ClienteRepository clienteRepository;
    private final GruClient gruClient;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;
    private final GruComprovantePdfService comprovantePdfService;
    private final DocumentoPdfService documentoPdfService;
    private final EmailService emailService;
    private final TenantQueryService tenantQueryService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Valor oficial da taxa da GRU (CHA-MTA-E). Usado como exibição no caminho de
     * BOLETO, que — diferente do PIX — não percorre o PagTesouro e portanto não
     * retorna o valor pela API. Vazio = não preenche (o valor consta no PDF do boleto).
     * Configure via {@code JETSKI_GRU_VALOR}. O PIX, quando gerado, sempre sobrescreve
     * com o valor real retornado pela Marinha.
     */
    @Value("${jetski.gru.valor:}")
    private String valorPadraoConfig;

    /** Resultado da tentativa de geração (sempre devolvido, mesmo em falha). */
    public record GruGeracao(
        boolean sucesso,
        ReservaHabilitacao habilitacao,
        String qrPngBase64,      // só no sucesso "fresco"; null se reaproveitada/falha
        boolean reaproveitada,
        String erroCodigo,
        String erroMensagem
    ) {}

    /** Resultado da geração do boleto (PDF). O PDF é baixado via {@link #baixarBoletoPdf}. */
    public record BoletoGeracao(
        boolean sucesso,
        ReservaHabilitacao habilitacao,
        boolean reaproveitada,
        String erroCodigo,
        String erroMensagem
    ) {}

    @Transactional
    public GruGeracao gerarGru(UUID reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + reservaId));
        Cliente cliente = clienteRepository.findById(reserva.getClienteId())
            .orElseThrow(() -> new NotFoundException("Cliente não encontrado: " + reserva.getClienteId()));

        ReservaHabilitacao hab = habilitacaoRepository.findByReservaId(reservaId)
            .orElseGet(() -> ReservaHabilitacao.builder()
                .tenantId(reserva.getTenantId())
                .reservaId(reservaId)
                .via(ReservaHabilitacao.Via.EMA)
                .build());

        // Idempotência: GRU já paga ou PIX ainda válido → reaproveita (não duplica)
        if (gruValidaReaproveitavel(hab)) {
            log.info("GRU reaproveitada para reserva {} (numero={})", reservaId, hab.getGruNumero());
            return new GruGeracao(true, hab, null, true, null, null);
        }

        GruContribuinte contrib = montarContribuinte(cliente);
        try {
            GruResultado r = gruClient.gerar(contrib);
            hab.setGruNumero(r.gruNumero());
            hab.setGruValor(r.gruValor());
            hab.setGruPixCopiaECola(r.pixCopiaECola());
            hab.setGruPixExpiracao(r.pixExpiracao());
            hab.setGruIdMarinha(r.idGru());
            hab.setGruIdSessao(r.idSessao());
            hab.setGruGeradaEm(Instant.now());
            ReservaHabilitacao salvo = habilitacaoRepository.save(hab);
            // Metering: geração real na Marinha (reaproveitada/falha não contam)
            eventPublisher.publishEvent(new GruEmitidaEvent(
                salvo.getTenantId(), reservaId, salvo.getId(), "PIX", salvo.getGruGeradaEm()));
            return new GruGeracao(true, salvo, r.pixQrPngBase64(), false, null, null);
        } catch (GruException e) {
            log.warn("Falha ao gerar GRU para reserva {}: {} - {}",
                reservaId, e.getCodigo(), e.getMessage());
            return new GruGeracao(false, hab, null, false,
                e.getCodigo().name(), e.getMessage());
        }
    }

    @Transactional
    public BoletoGeracao gerarBoleto(UUID reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + reservaId));
        Cliente cliente = clienteRepository.findById(reserva.getClienteId())
            .orElseThrow(() -> new NotFoundException("Cliente não encontrado: " + reserva.getClienteId()));

        ReservaHabilitacao hab = habilitacaoRepository.findByReservaId(reservaId)
            .orElseGet(() -> ReservaHabilitacao.builder()
                .tenantId(reserva.getTenantId())
                .reservaId(reservaId)
                .via(ReservaHabilitacao.Via.EMA)
                .build());

        // Idempotência: boleto já gerado e não pago → reaproveita o PDF, não duplica GRU.
        // Se o número ainda não foi preenchido, tenta extrair do PDF já salvo.
        if (hab.getGruPdfS3Key() != null && !Boolean.TRUE.equals(hab.getGruPago())) {
            if (hab.getGruNumero() == null) {
                try {
                    String num = gruClient.extrairNumeroDoBoleto(storageService.getObject(hab.getGruPdfS3Key()));
                    if (num != null) {
                        hab.setGruNumero(num);
                        hab = habilitacaoRepository.save(hab);
                    }
                } catch (Exception e) {
                    log.warn("Falha ao re-extrair número do boleto salvo (reserva {}): {}", reservaId, e.getMessage());
                }
            }
            return new BoletoGeracao(true, hab, true, null, null);
        }

        GruContribuinte contrib = montarContribuinte(cliente);
        try {
            GruBoletoResultado r = gruClient.gerarBoleto(contrib);
            String key = String.format("%s/reserva/%s/gru-boleto.pdf",
                reserva.getTenantId(), reservaId);
            storageService.putObject(key, r.pdf(), "application/pdf");

            hab.setGruPdfS3Key(key);
            hab.setGruIdMarinha(r.idGru());
            // Número de referência extraído do PDF (preenche automático na tela).
            if (r.gruNumero() != null && hab.getGruNumero() == null) {
                hab.setGruNumero(r.gruNumero());
            }
            hab.setGruGeradaEm(Instant.now());
            // O boleto não passa pelo PagTesouro → não retorna valor. Preenche com o
            // valor oficial configurado (se houver) para exibição no backoffice.
            if (hab.getGruValor() == null) {
                BigDecimal vp = valorPadrao();
                if (vp != null) {
                    hab.setGruValor(vp);
                }
            }
            ReservaHabilitacao salvo = habilitacaoRepository.save(hab);
            // Metering: geração real na Marinha (reaproveitada/falha não contam)
            eventPublisher.publishEvent(new GruEmitidaEvent(
                salvo.getTenantId(), reservaId, salvo.getId(), "BOLETO", salvo.getGruGeradaEm()));

            return new BoletoGeracao(true, salvo, false, null, null);
        } catch (GruException e) {
            log.warn("Falha ao gerar boleto da GRU para reserva {}: {} - {}",
                reservaId, e.getCodigo(), e.getMessage());
            return new BoletoGeracao(false, hab, false,
                e.getCodigo().name(), e.getMessage());
        }
    }

    /** Resultado da verificação de pagamento do PIX (sob demanda). */
    public record VerificacaoPagamento(
        boolean pago,
        String situacao,
        boolean comprovanteDisponivel
    ) {}

    /**
     * Verifica no PagTesouro se o PIX da GRU foi pago. Se sim, marca {@code gruPago},
     * grava {@code gruPagoEm} e gera o comprovante PDF. Best-effort: em falha de rede,
     * devolve o estado atual sem alterar.
     */
    @Transactional
    public VerificacaoPagamento verificarPagamento(UUID reservaId) {
        ReservaHabilitacao hab = habilitacaoRepository.findByReservaId(reservaId)
            .orElseThrow(() -> new NotFoundException("Habilitação não encontrada: " + reservaId));

        if (Boolean.TRUE.equals(hab.getGruPago())) {
            // Auto-heal: GRU paga mas resolvida ficou false (dados antigos).
            if (!Boolean.TRUE.equals(hab.getResolvida())
                    && hab.getVia() != ReservaHabilitacao.Via.CHA) {
                hab.setResolvida(true);
                habilitacaoRepository.save(hab);
            }
            return new VerificacaoPagamento(true, "CONCLUIDO", hab.getGruComprovanteS3Key() != null);
        }
        if (hab.getGruIdSessao() == null) {
            return new VerificacaoPagamento(false, "SEM_SESSAO", false);
        }

        try {
            GruPagamentoStatus st = gruClient.consultarStatusPix(hab.getGruIdSessao());
            if (!st.pago()) {
                return new VerificacaoPagamento(false, st.situacao(), false);
            }
            hab.setGruPago(true);
        notificarGruPaga(hab.getTenantId(), hab.getReservaId());
            hab.setGruPagoEm(st.dataPagamento() != null ? st.dataPagamento() : Instant.now());
            // EMA resolve quando a GRU é paga; CHA depende do número.
            hab.setResolvida(hab.getVia() == ReservaHabilitacao.Via.CHA
                ? (hab.getChaNumero() != null && !hab.getChaNumero().isBlank())
                : true);
            try {
                byte[] pdf = comprovantePdfService.gerar(st);
                String key = String.format("%s/reserva/%s/gru-comprovante.pdf",
                    hab.getTenantId(), reservaId);
                storageService.putObject(key, pdf, "application/pdf");
                hab.setGruComprovanteS3Key(key);
            } catch (Exception e) {
                log.warn("GRU paga mas falhou ao gerar/armazenar comprovante (reserva {}): {}",
                    reservaId, e.getMessage());
            }
            habilitacaoRepository.save(hab);
            log.info("GRU paga confirmada para reserva {} (pagoEm={})", reservaId, hab.getGruPagoEm());
            return new VerificacaoPagamento(true, "CONCLUIDO", hab.getGruComprovanteS3Key() != null);
        } catch (GruException e) {
            log.warn("Falha ao verificar pagamento da GRU (reserva {}): {}", reservaId, e.getMessage());
            return new VerificacaoPagamento(false, "ERRO", false);
        }
    }

    /**
     * Registra um comprovante de pagamento da GRU enviado <b>manualmente</b> — pago
     * por outro meio (boleto, caixa, transferência) ou quando a verificação automática
     * do PIX não funcionou. Converte imagem em PDF (para anexar à documentação da
     * Marinha), marca a GRU como paga e resolve a habilitação EMA.
     *
     * @param conteudo  bytes do comprovante (imagem ou PDF)
     * @param ehImagem  true se {@code conteudo} é imagem (será embutida num PDF); false se já é PDF
     */
    @Transactional
    public void registrarComprovanteManual(UUID reservaId, byte[] conteudo, boolean ehImagem) {
        ReservaHabilitacao hab = habilitacaoRepository.findByReservaId(reservaId)
            .orElseThrow(() -> new NotFoundException("Habilitação não encontrada: " + reservaId));
        if (conteudo == null || conteudo.length == 0) {
            throw new BusinessException("Comprovante vazio");
        }

        byte[] pdf = ehImagem
            ? documentoPdfService.imagemParaPdf(conteudo, "COMPROVANTE DE PAGAMENTO DA GRU").conteudo()
            : conteudo;
        String key = String.format("%s/reserva/%s/gru-comprovante.pdf", hab.getTenantId(), reservaId);
        storageService.putObject(key, pdf, "application/pdf");

        hab.setGruComprovanteS3Key(key);
        hab.setGruPago(true);
        notificarGruPaga(hab.getTenantId(), hab.getReservaId());
        if (hab.getGruPagoEm() == null) {
            hab.setGruPagoEm(Instant.now());
        }
        // EMA resolve quando a GRU é paga; CHA depende do número da CHA.
        hab.setResolvida(hab.getVia() == ReservaHabilitacao.Via.CHA
            ? (hab.getChaNumero() != null && !hab.getChaNumero().isBlank())
            : true);
        habilitacaoRepository.save(hab);
        log.info("Comprovante manual da GRU registrado para reserva {} (gruPago=true)", reservaId);
    }

    private BigDecimal valorPadrao() {
        if (valorPadraoConfig == null || valorPadraoConfig.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(valorPadraoConfig.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            log.warn("jetski.gru.valor inválido: '{}'", valorPadraoConfig);
            return null;
        }
    }

    /** Lê os bytes do comprovante de pagamento já gerado. */
    @Transactional(readOnly = true)
    public byte[] baixarComprovantePdf(UUID reservaId) {
        ReservaHabilitacao hab = habilitacaoRepository.findByReservaId(reservaId)
            .orElseThrow(() -> new NotFoundException("Habilitação não encontrada: " + reservaId));
        if (hab.getGruComprovanteS3Key() == null) {
            throw new NotFoundException("Comprovante da GRU ainda não disponível para a reserva " + reservaId);
        }
        return storageService.getObject(hab.getGruComprovanteS3Key());
    }

    /** Lê os bytes do PDF do boleto já gerado (stream autenticado pelo backend). */
    @Transactional(readOnly = true)
    public byte[] baixarBoletoPdf(UUID reservaId) {
        ReservaHabilitacao hab = habilitacaoRepository.findByReservaId(reservaId)
            .orElseThrow(() -> new NotFoundException("Habilitação não encontrada: " + reservaId));
        if (hab.getGruPdfS3Key() == null) {
            throw new NotFoundException("Boleto da GRU ainda não gerado para a reserva " + reservaId);
        }
        return storageService.getObject(hab.getGruPdfS3Key());
    }

    /** Envia ao cliente o 1º e-mail com o número da GRU (+ PIX/valor). Best-effort. */
    @Transactional(readOnly = true)
    public boolean enviarEmailGru(UUID reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + reservaId));
        Cliente cliente = clienteRepository.findById(reserva.getClienteId())
            .orElseThrow(() -> new NotFoundException("Cliente não encontrado: " + reserva.getClienteId()));
        ReservaHabilitacao hab = habilitacaoRepository.findByReservaId(reservaId)
            .orElseThrow(() -> new NotFoundException("Habilitação não encontrada: " + reservaId));

        String email = cliente.getEmail();
        if (hab.getGruNumero() == null || email == null || email.isBlank()) {
            return false;
        }
        Tenant tenant = tenantQueryService.findById(reserva.getTenantId());
        String loja = tenant != null ? tenant.getRazaoSocial() : "";
        StringBuilder b = new StringBuilder();
        b.append("<p>Olá, ").append(cliente.getNome()).append("!</p>");
        b.append("<p>Sua GRU (taxa da Marinha) foi gerada:</p><ul>");
        b.append("<li><b>Número da GRU:</b> ").append(hab.getGruNumero()).append("</li>");
        if (hab.getGruValor() != null) {
            b.append("<li><b>Valor:</b> R$ ").append(hab.getGruValor().toPlainString().replace('.', ',')).append("</li>");
        }
        if (hab.getGruPixExpiracao() != null) {
            b.append("<li><b>Vencimento do PIX:</b> ").append(hab.getGruPixExpiracao()).append("</li>");
        }
        b.append("</ul>");
        if (hab.getGruPixCopiaECola() != null) {
            b.append("<p><b>PIX copia-e-cola:</b></p><p style=\"word-break:break-all;font-family:monospace\">")
             .append(hab.getGruPixCopiaECola()).append("</p>");
        }
        b.append("<p>Após o pagamento, siga com os próximos passos do seu atendimento.</p>");
        b.append("<p>").append(loja).append("</p>");

        emailService.sendEmail(email, "Sua GRU — " + loja, b.toString());
        log.info("E-mail da GRU enviado ao cliente da reserva {} (gru={})", reservaId, hab.getGruNumero());
        return true;
    }

    private boolean gruValidaReaproveitavel(ReservaHabilitacao hab) {
        if (Boolean.TRUE.equals(hab.getGruPago())) {
            return true;
        }
        boolean temPix = hab.getGruNumero() != null && hab.getGruPixCopiaECola() != null;
        boolean naoVencida = hab.getGruPixExpiracao() == null
            || hab.getGruPixExpiracao().isAfter(Instant.now());
        return temPix && naoVencida;
    }

    private GruContribuinte montarContribuinte(Cliente c) {
        JsonNode end = parseEndereco(c.getEnderecoJson());
        return new GruContribuinte(
            soDigitos(c.getDocumento()),
            c.getNome(),
            c.getTelefone() != null ? c.getTelefone() : c.getWhatsapp(),
            c.getEmail(),
            mapSexo(c.getGenero()),
            text(end, "cep"),
            text(end, "logradouro"),
            text(end, "numero"),
            text(end, "complemento"),
            text(end, "bairro"),
            text(end, "cidade", "municipio"),
            text(end, "uf", "estado"));
    }

    private JsonNode parseEndereco(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("enderecoJson do cliente inválido ao montar GRU");
            return objectMapper.createObjectNode();
        }
    }

    private static String text(JsonNode n, String... chaves) {
        for (String k : chaves) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return "";
    }

    private static String mapSexo(String genero) {
        if (genero == null || genero.isBlank()) {
            return "";
        }
        char c = Character.toUpperCase(genero.trim().charAt(0));
        return c == 'M' ? "M" : c == 'F' ? "F" : "";
    }

    private static String soDigitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    /** Notifica o cliente que a GRU foi confirmada (best-effort). */
    private void notificarGruPaga(java.util.UUID tenantId, java.util.UUID reservaId) {
        reservaRepository.findById(reservaId).ifPresent(r ->
            clienteNotificacaoService.notificar(tenantId, r.getClienteId(),
                com.jetski.locacoes.domain.ClienteNotificacao.GRU_PAGA,
                "Pagamento da GRU confirmado ✅",
                "A taxa da Marinha foi confirmada — sua habilitação segue para a próxima etapa.",
                "/conta/reservas/" + reservaId + "/habilitacao"));
    }
}
