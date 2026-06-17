package com.jetski.locacoes.api;

import com.jetski.locacoes.internal.EmissaoService;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * API de emissão dos documentos consolidados de uma reserva (balcão).
 * POST /v1/tenants/{tenantId}/reservas/{id}/emitir-documentos
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/reservas/{id}/emitir-documentos")
@RequiredArgsConstructor
@Slf4j
public class EmissaoController {

    private final EmissaoService emissaoService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Emitir documentos consolidados",
        description = "Gera o PDF (anexos + termo), arquiva, registra, envia à Marinha e ao cliente, " +
                      "e devolve a URL de download + dados da GRU. Exige habilitação resolvida e termos assinados."
    )
    public ResponseEntity<EmissaoService.ResultadoEmissao> emitir(
        @Parameter(description = "UUID do tenant") @PathVariable UUID tenantId,
        @Parameter(description = "UUID da reserva") @PathVariable UUID id
    ) {
        log.info("POST /v1/tenants/{}/reservas/{}/emitir-documentos", tenantId, id);
        validateTenantContext(tenantId);
        return ResponseEntity.ok(emissaoService.emitir(id));
    }

    private void validateTenantContext(UUID tenantId) {
        UUID contextTenantId = TenantContext.getTenantId();
        if (!tenantId.equals(contextTenantId)) {
            log.error("Tenant mismatch: path={}, context={}", tenantId, contextTenantId);
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
    }
}
