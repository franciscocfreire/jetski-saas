package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.HabilitacaoRequest;
import com.jetski.locacoes.api.dto.HabilitacaoResponse;
import com.jetski.locacoes.domain.ReservaHabilitacao;
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
            .resolvida(h.getResolvida())
            .createdAt(h.getCreatedAt())
            .updatedAt(h.getUpdatedAt())
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
