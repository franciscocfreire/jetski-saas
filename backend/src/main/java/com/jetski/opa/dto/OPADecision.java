package com.jetski.opa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Decisão estruturada retornada pelas políticas OPA.
 *
 * Suporta tanto RBAC simples (boolean allow) quanto
 * Alçadas complexas (allow + requer_aprovacao + aprovador_requerido).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OPADecision {

    /**
     * Decisão principal: operação permitida?
     */
    private Boolean allow;

    /**
     * Operação requer aprovação de superior?
     */
    @JsonProperty("requer_aprovacao")
    private Boolean requerAprovacao;

    /**
     * Qual role deve aprovar (ex: "ADMIN_TENANT")
     */
    @JsonProperty("aprovador_requerido")
    private String aprovadorRequerido;

    /**
     * Tenant válido (validação cross-tenant)
     */
    @JsonProperty("tenant_is_valid")
    private Boolean tenantIsValid;

    /**
     * Helper: decisão foi permitida?
     */
    public boolean isAllowed() {
        return Boolean.TRUE.equals(allow);
    }

    /**
     * Helper: decisão requer aprovação?
     */
    public boolean requiresApproval() {
        return Boolean.TRUE.equals(requerAprovacao);
    }

    /**
     * Helper: tenant é válido?
     */
    public boolean isTenantValid() {
        return tenantIsValid == null || Boolean.TRUE.equals(tenantIsValid);
    }
}
