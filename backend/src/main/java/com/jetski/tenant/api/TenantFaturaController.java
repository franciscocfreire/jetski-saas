package com.jetski.tenant.api;

import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.domain.Fatura;
import com.jetski.tenant.internal.FaturaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Faturas da assinatura na visão da EMPRESA. Restrito ao dono
 * (ADMIN_TENANT) — OPA cobre via wildcard do papel; billing não é
 * operação de OPERADOR/FINANCEIRO de loja.
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/faturas")
@RequiredArgsConstructor
public class TenantFaturaController {

    private final FaturaService faturaService;

    /** Plano atual + faturas (página "Plano e faturas"). */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    public Map<String, Object> minhas(@PathVariable UUID tenantId) {
        validar(tenantId);
        List<Fatura> faturas = faturaService.minhas(tenantId);
        Map<String, Object> plano = faturaService.planoAtual(tenantId);
        return Map.of("plano", plano, "faturas", faturas, "uso", faturaService.uso(tenantId));
    }

    /** Empresa informa o pagamento (txid do PIX) → EM_CONFERENCIA. */
    @PostMapping("/{faturaId}/informar-pagamento")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    public Fatura informarPagamento(
            @PathVariable UUID tenantId,
            @PathVariable UUID faturaId,
            @RequestBody Map<String, String> body) {
        validar(tenantId);
        return faturaService.informarPagamento(tenantId, faturaId, body.get("txid"));
    }

    private void validar(UUID tenantId) {
        if (!tenantId.equals(TenantContext.getTenantId())) {
            throw new com.jetski.shared.exception.BusinessException(
                "Tenant do path difere do contexto autenticado");
        }
    }
}
