package jetski.authorization

import data.jetski.rbac
import data.jetski.alcada
import data.jetski.multi_tenant
import data.jetski.business
import data.jetski.context

import future.keywords.if
import future.keywords.in

# =============================================================================
# Main Authorization Policy
# =============================================================================
#
# Combina todas as políticas (RBAC, Alçada, Multi-tenant, Business, Context)
# e retorna decisão final de autorização.
#
# Ordem de avaliação:
# 1. Multi-tenant validation (obrigatório)
# 2. RBAC (role-based permissions)
# 3. Alçada (approval authority)
# 4. Business rules
# 5. Context (horário, IP, device)
#
# Decisão final:
# - allow: true/false
# - tenant_is_valid: true/false
# - requer_aprovacao: true/false
# - aprovador_requerido: string ou null
# - deny_reasons: array de strings
# - warnings: array de strings
#
# =============================================================================

# =============================================================================
# 1. Multi-Tenant Validation (Obrigatório)
# =============================================================================

tenant_is_valid := multi_tenant.multi_tenant_valid

# Se tenant inválido, nega imediatamente
deny contains msg if {
    not tenant_is_valid
    some msg in multi_tenant.multi_tenant_deny
}

# =============================================================================
# 2. Platform Admin Bypass
# =============================================================================

# Platform admins (unrestricted_access=true) bypassam RBAC
is_platform_admin if {
    input.user.unrestricted_access == true
}

platform_allow := rbac.platform_allow

# =============================================================================
# 3. RBAC Validation
# =============================================================================

rbac_allow := rbac.rbac_allow

# =============================================================================
# 4. Alçada Validation
# =============================================================================

alcada_allow := alcada.alcada_allow

alcada_requer_aprovacao := alcada.alcada_requer_aprovacao

alcada_aprovador_requerido := alcada.alcada_aprovador_requerido

# =============================================================================
# 5. Business Rules Validation
# =============================================================================

business_allow := business.business_allow

# Coleta deny reasons de business rules
deny contains msg if {
    some msg in business.business_deny
}

# =============================================================================
# 6. Context Validation
# =============================================================================

context_allow := context.context_allow

# Coleta deny reasons de context
deny contains msg if {
    some msg in context.context_deny
}

# Coleta warnings de context
warnings contains msg if {
    some msg in context.context_warnings
}

# =============================================================================
# Main Decision Logic
# =============================================================================

# Decisão final: permite se passou em todas as validações
default allow := false

# Platform admin sempre permite (para ações de plataforma)
allow if {
    is_platform_admin
    platform_allow
}

# Autorização normal: RBAC + Alçada + Business + Context + Tenant
allow if {
    tenant_is_valid
    rbac_allow
    alcada_allow
    business_allow
    context_allow
}

# Autorização sem alçada (ações que não requerem alçada)
allow if {
    tenant_is_valid
    rbac_allow
    not alcada_requer_aprovacao
    business_allow
    context_allow
}

# =============================================================================
# Requer Aprovação (Se RBAC ok, mas alçada insuficiente)
# =============================================================================

requer_aprovacao if {
    tenant_is_valid
    rbac_allow
    alcada_requer_aprovacao
    business_allow
    context_allow
}

aprovador_requerido := alcada_aprovador_requerido

# =============================================================================
# Response Structure
# =============================================================================

# Estrutura de resposta compatível com OPADecision.java
result := {
    "allow": allow,
    "tenant_is_valid": tenant_is_valid,
    "requer_aprovacao": requer_aprovacao,
    "aprovador_requerido": object.get(aprovador_requerido, null, null),
    "deny_reasons": deny,
    "warnings": warnings,
    "evaluated_policies": {
        "rbac": rbac_allow,
        "alcada": alcada_allow,
        "business": business_allow,
        "context": context_allow,
        "multi_tenant": tenant_is_valid
    }
}
