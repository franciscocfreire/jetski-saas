package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.HabilitacaoGruBoletoResponse;
import com.jetski.locacoes.api.dto.HabilitacaoGruPagamentoResponse;
import com.jetski.locacoes.api.dto.HabilitacaoGruResponse;
import com.jetski.locacoes.api.dto.HabilitacaoRequest;
import com.jetski.locacoes.api.dto.HabilitacaoResponse;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.internal.GruService;
import com.jetski.locacoes.internal.HabilitacaoService;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * API de habilitação do condutor de uma reserva (CHA ou CHA-MTA-E/EMA + GRU).
 * Endpoint sub-recurso: /v1/tenants/{tenantId}/reservas/{id}/habilitacao
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/reservas/{id}/habilitacao")
@RequiredArgsConstructor
@Slf4j
public class HabilitacaoController {

    private final HabilitacaoService habilitacaoService;
    private final GruService gruService;

    @PostMapping("/gru")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Gerar GRU + PIX automaticamente (Marinha/PagTesouro)",
        description = "Emite a GRU da Capitania e gera o PIX (copia-e-cola + QR). " +
                      "Reaproveita GRU válida já existente. Em falha, sucesso=false + erroCodigo " +
                      "(o operador segue pelo fluxo manual)."
    )
    public ResponseEntity<HabilitacaoGruResponse> gerarGru(
        @Parameter(description = "UUID do tenant") @PathVariable UUID tenantId,
        @Parameter(description = "UUID da reserva") @PathVariable UUID id
    ) {
        log.info("POST /v1/tenants/{}/reservas/{}/habilitacao/gru", tenantId, id);
        validateTenantContext(tenantId);
        return ResponseEntity.ok(toGruResponse(gruService.gerarGru(id)));
    }

    @PostMapping("/gru/boleto")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Gerar boleto da GRU (PDF) automaticamente",
        description = "Emite a GRU da Capitania na modalidade boleto e devolve a URL do PDF. " +
                      "Reaproveita boleto já gerado e não pago. Em falha, sucesso=false + erroCodigo."
    )
    public ResponseEntity<HabilitacaoGruBoletoResponse> gerarBoleto(
        @Parameter(description = "UUID do tenant") @PathVariable UUID tenantId,
        @Parameter(description = "UUID da reserva") @PathVariable UUID id
    ) {
        log.info("POST /v1/tenants/{}/reservas/{}/habilitacao/gru/boleto", tenantId, id);
        validateTenantContext(tenantId);
        GruService.BoletoGeracao g = gruService.gerarBoleto(id);
        return ResponseEntity.ok(HabilitacaoGruBoletoResponse.builder()
            .sucesso(g.sucesso())
            .reaproveitada(g.reaproveitada())
            .idMarinha(g.habilitacao().getGruIdMarinha())
            .gruNumero(g.habilitacao().getGruNumero())
            .gruValor(g.habilitacao().getGruValor())
            .erroCodigo(g.erroCodigo())
            .erroMensagem(g.erroMensagem())
            .build());
    }

    @PutMapping("/gru/comprovante")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Registrar comprovante de pagamento da GRU (manual)",
        description = "Para quando a GRU foi paga por outro meio ou a verificação automática do PIX " +
                      "não funcionou. Recebe a imagem/PDF do comprovante (base64/dataURL), marca a GRU " +
                      "como paga e a anexa à documentação da Marinha."
    )
    public ResponseEntity<HabilitacaoResponse> registrarComprovante(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @RequestBody ComprovanteRequest request
    ) {
        validateTenantContext(tenantId);
        byte[] conteudo = decodeBase64(request.conteudoBase64());
        gruService.registrarComprovanteManual(id, conteudo, !ehPdf(conteudo));
        return habilitacaoService.getByReserva(id)
            .map(h -> ResponseEntity.ok(toResponse(h)))
            .orElse(ResponseEntity.notFound().build());
    }

    /** Corpo do upload do comprovante manual (imagem ou PDF em base64/dataURL). */
    public record ComprovanteRequest(String conteudoBase64) {}

    @PutMapping("/devolutiva")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Anexar a devolutiva da Marinha (CHA-MTA-E confirmada)",
        description = "A Marinha responde manualmente por e-mail à loja. Anexar o documento devolvido " +
                      "confirma a habilitação temporária — só então ela fica elegível para reuso em " +
                      "novas reservas (30 dias da emissão). Re-upload substitui o PDF."
    )
    public ResponseEntity<HabilitacaoResponse> registrarDevolutiva(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @RequestBody ComprovanteRequest request
    ) {
        validateTenantContext(tenantId);
        byte[] conteudo = decodeBase64(request.conteudoBase64());
        return ResponseEntity.ok(toResponse(habilitacaoService.registrarDevolutivaMarinha(
            id, conteudo, !ehPdf(conteudo),
            com.jetski.shared.security.TenantContext.getUsuarioId())));
    }

    @GetMapping("/devolutiva/download")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Baixar a devolutiva da Marinha (PDF, streaming)")
    public ResponseEntity<byte[]> baixarDevolutiva(
        @PathVariable UUID tenantId,
        @PathVariable UUID id
    ) {
        validateTenantContext(tenantId);
        byte[] pdf = habilitacaoService.baixarDevolutivaPdf(id);
        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"cha-mtae-confirmada.pdf\"")
            .body(pdf);
    }

    private static byte[] decodeBase64(String conteudo) {
        String b64 = conteudo == null ? "" : conteudo.trim();
        if (b64.startsWith("data:")) {
            int comma = b64.indexOf(',');
            if (comma > 0) {
                b64 = b64.substring(comma + 1);
            }
        }
        if (b64.isBlank()) {
            throw new BusinessException("Comprovante vazio");
        }
        return java.util.Base64.getDecoder().decode(b64);
    }

    private static boolean ehPdf(byte[] b) {
        return b != null && b.length >= 4
            && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }

    @PostMapping("/gru/verificar-pagamento")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Verificar se o PIX da GRU foi pago (PagTesouro)",
        description = "Consulta o pix-stn/sonda; se pago, marca a GRU como paga e gera o comprovante."
    )
    public ResponseEntity<HabilitacaoGruPagamentoResponse> verificarPagamento(
        @PathVariable UUID tenantId,
        @PathVariable UUID id
    ) {
        validateTenantContext(tenantId);
        GruService.VerificacaoPagamento v = gruService.verificarPagamento(id);
        return ResponseEntity.ok(HabilitacaoGruPagamentoResponse.builder()
            .pago(v.pago())
            .situacao(v.situacao())
            .comprovanteDisponivel(v.comprovanteDisponivel())
            .build());
    }

    @PostMapping("/gru/enviar-email")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Enviar ao cliente o e-mail com o número da GRU")
    public ResponseEntity<java.util.Map<String, Boolean>> enviarEmailGru(
        @PathVariable UUID tenantId,
        @PathVariable UUID id
    ) {
        validateTenantContext(tenantId);
        return ResponseEntity.ok(java.util.Map.of("enviado", gruService.enviarEmailGru(id)));
    }

    @GetMapping("/gru/comprovante/download")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Baixar o comprovante de pagamento da GRU (PDF, streaming)")
    public ResponseEntity<byte[]> baixarComprovante(
        @PathVariable UUID tenantId,
        @PathVariable UUID id
    ) {
        validateTenantContext(tenantId);
        byte[] pdf = gruService.baixarComprovantePdf(id);
        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"gru-comprovante.pdf\"")
            .body(pdf);
    }

    @GetMapping("/gru/boleto/download")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Baixar o PDF do boleto da GRU (streaming autenticado)")
    public ResponseEntity<byte[]> baixarBoleto(
        @PathVariable UUID tenantId,
        @PathVariable UUID id
    ) {
        validateTenantContext(tenantId);
        byte[] pdf = gruService.baixarBoletoPdf(id);
        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"gru-boleto.pdf\"")
            .body(pdf);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Registrar habilitação do condutor",
        description = "Registra a habilitação: via CHA (já habilitado) ou EMA (emissão CHA-MTA-E + GRU manual). " +
                      "resolvida = CHA coletada OU GRU paga."
    )
    public ResponseEntity<HabilitacaoResponse> registrar(
        @Parameter(description = "UUID do tenant") @PathVariable UUID tenantId,
        @Parameter(description = "UUID da reserva") @PathVariable UUID id,
        @Valid @RequestBody HabilitacaoRequest request
    ) {
        log.info("PUT /v1/tenants/{}/reservas/{}/habilitacao - via: {}", tenantId, id, request.getVia());
        validateTenantContext(tenantId);

        ReservaHabilitacao saved = habilitacaoService.registrar(id, toEntity(request));
        return ResponseEntity.ok(toResponse(saved));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Consultar habilitação da reserva")
    public ResponseEntity<HabilitacaoResponse> get(
        @PathVariable UUID tenantId,
        @PathVariable UUID id
    ) {
        validateTenantContext(tenantId);
        return habilitacaoService.getByReserva(id)
            .map(h -> ResponseEntity.ok(toResponse(h)))
            .orElse(ResponseEntity.notFound().build());
    }

    private ReservaHabilitacao.Via parseVia(String via) {
        try {
            return ReservaHabilitacao.Via.valueOf(via.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException("via inválida: " + via + " (use CHA ou EMA)");
        }
    }

    private ReservaHabilitacao toEntity(HabilitacaoRequest r) {
        return ReservaHabilitacao.builder()
            .via(parseVia(r.getVia()))
            .chaCategoria(r.getChaCategoria())
            .chaNumero(r.getChaNumero())
            .chaValidade(r.getChaValidade())
            .videoaulaEm(Boolean.TRUE.equals(r.getVideoaulaAssistida()) ? Instant.now() : null)
            .anexoSaude(r.getAnexoSaude())
            .anexoRegras(r.getAnexoRegras())
            .anexoResidencia(r.getAnexoResidencia())
            .usaLentes(r.getUsaLentes())
            .usaAparelho(r.getUsaAparelho())
            .instrutorId(r.getInstrutorId())
            .gruNumero(r.getGruNumero())
            .gruValor(r.getGruValor())
            .gruPago(r.getGruPago())
            .build();
    }

    private HabilitacaoResponse toResponse(ReservaHabilitacao h) {
        return HabilitacaoResponse.builder()
            .id(h.getId())
            .reservaId(h.getReservaId())
            .via(h.getVia() != null ? h.getVia().name() : null)
            .chaCategoria(h.getChaCategoria())
            .chaNumero(h.getChaNumero())
            .chaValidade(h.getChaValidade())
            .videoaulaEm(h.getVideoaulaEm())
            .anexoSaude(h.getAnexoSaude())
            .anexoRegras(h.getAnexoRegras())
            .anexoResidencia(h.getAnexoResidencia())
            .usaLentes(h.getUsaLentes())
            .usaAparelho(h.getUsaAparelho())
            .instrutorId(h.getInstrutorId())
            .gruNumero(h.getGruNumero())
            .gruValor(h.getGruValor())
            .gruPago(h.getGruPago())
            .gruPagoEm(h.getGruPagoEm())
            .gruPixCopiaECola(h.getGruPixCopiaECola())
            .gruPixExpiracao(h.getGruPixExpiracao())
            .gruBoletoDisponivel(h.getGruPdfS3Key() != null)
            .gruComprovanteDisponivel(h.getGruComprovanteS3Key() != null)
            .marinhaConfirmadaEm(h.getMarinhaConfirmadaEm())
            .devolutivaDisponivel(h.getChaMtaeS3Key() != null)
            .resolvida(h.getResolvida())
            .createdAt(h.getCreatedAt())
            .updatedAt(h.getUpdatedAt())
            .build();
    }

    private HabilitacaoGruResponse toGruResponse(GruService.GruGeracao g) {
        ReservaHabilitacao h = g.habilitacao();
        return HabilitacaoGruResponse.builder()
            .sucesso(g.sucesso())
            .reaproveitada(g.reaproveitada())
            .gruNumero(h.getGruNumero())
            .gruValor(h.getGruValor())
            .gruPago(h.getGruPago())
            .pixCopiaECola(h.getGruPixCopiaECola())
            .pixQrPngBase64(g.qrPngBase64())
            .pixExpiracao(h.getGruPixExpiracao())
            .idMarinha(h.getGruIdMarinha())
            .erroCodigo(g.erroCodigo())
            .erroMensagem(g.erroMensagem())
            .build();
    }

    private void validateTenantContext(UUID tenantId) {
        UUID contextTenantId = TenantContext.getTenantId();
        if (!tenantId.equals(contextTenantId)) {
            log.error("Tenant mismatch: path={}, context={}", tenantId, contextTenantId);
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
    }
}
