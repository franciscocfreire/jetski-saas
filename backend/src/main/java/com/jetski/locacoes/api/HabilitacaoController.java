package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.HabilitacaoGruBoletoResponse;
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
            .erroCodigo(g.erroCodigo())
            .erroMensagem(g.erroMensagem())
            .build());
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
            .usaLentes(Boolean.TRUE.equals(r.getUsaLentes()))
            .usaAparelho(Boolean.TRUE.equals(r.getUsaAparelho()))
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
            .gruNumero(h.getGruNumero())
            .gruValor(h.getGruValor())
            .gruPago(h.getGruPago())
            .gruPagoEm(h.getGruPagoEm())
            .gruPixCopiaECola(h.getGruPixCopiaECola())
            .gruPixExpiracao(h.getGruPixExpiracao())
            .gruBoletoDisponivel(h.getGruPdfS3Key() != null)
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
