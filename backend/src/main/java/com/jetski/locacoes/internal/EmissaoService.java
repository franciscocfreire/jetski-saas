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
    private final ReservaHabilitacaoRepository habilitacaoRepository;
    private final ReservaAceiteRepository aceiteRepository;
    private final DocumentoEmitidoRepository documentoRepository;
    private final StorageService storageService;
    private final EmailService emailService;
    private final TenantQueryService tenantQueryService;
    private final DocumentoPdfService documentoPdfService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

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

        byte[] assinatura = (aceite.getAssinaturaS3Key() != null)
            ? storageService.getObject(aceite.getAssinaturaS3Key())
            : null;

        DocumentoPdfService.DadosDocumento dados = montarDados(reserva, cliente, hab, tenant);
        DocumentoPdfService.DocumentoPdf pdf = documentoPdfService.gerarDocumentoConsolidado(dados, assinatura);

        String key = String.format("%s/reserva/%s/documento.pdf", reserva.getTenantId(), reservaId);
        storageService.putObject(key, pdf.conteudo(), "application/pdf");

        String marinhaEmail = tenant.getMarinhaEmail();
        String clienteEmail = cliente.getEmail();

        DocumentoEmitido doc = documentoRepository.save(DocumentoEmitido.builder()
            .tenantId(reserva.getTenantId())
            .reservaId(reservaId)
            .s3Key(key)
            .hashSha256(pdf.sha256())
            .destinos(destinosJson(marinhaEmail, clienteEmail))
            .emitidoEm(Instant.now())
            .build());

        reserva.setDocumentoEmitidoEm(Instant.now());
        reservaRepository.save(reserva);

        boolean enviadoMarinha = enviar(marinhaEmail,
            "Documentos NORMAM-212 — reserva " + reservaId, corpoMarinha(cliente), pdf.conteudo());
        boolean enviadoCliente = enviar(clienteEmail,
            "Seus documentos — " + tenant.getRazaoSocial(), corpoCliente(cliente), pdf.conteudo());

        eventPublisher.publishEvent(DocumentosEmitidosEvent.of(
            reserva.getTenantId(), reservaId, doc.getId(),
            destinosResumo(enviadoMarinha, enviadoCliente), TenantContext.getUsuarioId()));

        String downloadUrl = storageService.generatePresignedDownloadUrl(key, 15).getUrl();
        log.info("Documentos emitidos: reservaId={}, docId={}, marinha={}, cliente={}",
            reservaId, doc.getId(), enviadoMarinha, enviadoCliente);

        return ResultadoEmissao.builder()
            .documentoId(doc.getId())
            .s3Key(key)
            .hashSha256(pdf.sha256())
            .downloadUrl(downloadUrl)
            .gruNumero(hab.getGruNumero())
            .gruValor(hab.getGruValor() != null ? hab.getGruValor().toPlainString() : null)
            .enviadoMarinha(enviadoMarinha)
            .enviadoCliente(enviadoCliente)
            .build();
    }

    private DocumentoPdfService.DadosDocumento montarDados(Reserva reserva, Cliente cliente,
                                                          ReservaHabilitacao hab, Tenant tenant) {
        String[] end = parseEndereco(cliente.getEnderecoJson());
        return new DocumentoPdfService.DadosDocumento(
            cliente.getNome(), cliente.getDocumento(), null, null,
            null, null, cliente.getTelefone(), cliente.getWhatsapp(), cliente.getEmail(),
            end[0], end[1], end[2],
            tenant.getRazaoSocial(), tenant.getCnpj(),
            tenant.getCidade(), null, null,
            hab.getVia() != null ? hab.getVia().name() : "EMA",
            Boolean.TRUE.equals(hab.getAnexoResidencia()),
            false, false, true,
            null, null, null, null, null,
            hab.getGruNumero(), hab.getGruValor() != null ? hab.getGruValor().toPlainString() : null);
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

    private boolean enviar(String to, String subject, String htmlBody, byte[] pdf) {
        if (to == null || to.isBlank()) {
            return false;
        }
        emailService.sendEmailComAnexo(to, subject, htmlBody, "documentos.pdf", pdf, "application/pdf");
        return true;
    }

    private String corpoMarinha(Cliente c) {
        return "<p>Segue em anexo a documentação (NORMAM-212/DPC) referente ao locatário "
            + "<b>" + safe(c.getNome()) + "</b> (CPF " + safe(c.getDocumento()) + ").</p>";
    }

    private String corpoCliente(Cliente c) {
        return "<p>Olá " + safe(c.getNome()) + ",</p><p>Seguem em anexo seus documentos do passeio.</p>";
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
