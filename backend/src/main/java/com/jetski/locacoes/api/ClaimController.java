package com.jetski.locacoes.api;

import com.jetski.locacoes.internal.ClaimService;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * API (staff/balcão) para emitir/reenviar o claim-token de ativação do cliente.
 *
 * <ul>
 *   <li>POST /v1/tenants/{tenantId}/clientes/{id}/claim          → cliente:claim</li>
 *   <li>POST /v1/tenants/{tenantId}/clientes/{id}/claim/reenviar → cliente:reenviar</li>
 * </ul>
 * A validação (ativação) é feita pelo cliente no endpoint público
 * {@code POST /v1/public/clientes/claim/validar}.
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/clientes/{id}/claim")
@Tag(name = "Clientes", description = "Ativação de conta do cliente (balcão)")
@RequiredArgsConstructor
@Slf4j
public class ClaimController {

    private final ClaimService claimService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Gerar e enviar claim-token de ativação ao cliente")
    public ResponseEntity<ClaimService.ClaimResult> gerar(
        @Parameter(description = "UUID do tenant") @PathVariable UUID tenantId,
        @Parameter(description = "UUID do cliente") @PathVariable UUID id,
        @RequestParam(required = false) String canais
    ) {
        log.info("POST /v1/tenants/{}/clientes/{}/claim canais={}", tenantId, id, canais);
        validateTenantContext(tenantId);
        return ResponseEntity.ok(claimService.gerar(id, canais));
    }

    @PostMapping("/reenviar")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Reenviar claim-token (desativa o anterior e emite outro)")
    public ResponseEntity<ClaimService.ClaimResult> reenviar(
        @Parameter(description = "UUID do tenant") @PathVariable UUID tenantId,
        @Parameter(description = "UUID do cliente") @PathVariable UUID id,
        @RequestParam(required = false) String canais
    ) {
        log.info("POST /v1/tenants/{}/clientes/{}/claim/reenviar canais={}", tenantId, id, canais);
        validateTenantContext(tenantId);
        return ResponseEntity.ok(claimService.gerar(id, canais));
    }

    private void validateTenantContext(UUID tenantId) {
        UUID contextTenantId = TenantContext.getTenantId();
        if (!tenantId.equals(contextTenantId)) {
            log.error("Tenant mismatch: path={}, context={}", tenantId, contextTenantId);
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
    }
}
