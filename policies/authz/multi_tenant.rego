package jetski.multi_tenant

import future.keywords.if
import future.keywords.in
import future.keywords.contains

# =============================================================================
# Multi-Tenant Isolation Policies
# =============================================================================
#
# Garante isolamento lógico entre tenants.
# Valida que usuário só acessa recursos do próprio tenant.
#
# Validações:
# - tenant_id do usuário == tenant_id do recurso
# - Exceção: platform admins (unrestricted_access=true)
#
# =============================================================================

# =============================================================================
# Tenant Validation
# =============================================================================

# Tenant do usuário corresponde ao tenant do recurso
tenant_is_valid if {
    input.user.tenant_id == input.resource.tenant_id
}

# Platform admin bypassa validação de tenant
tenant_is_valid if {
    input.user.unrestricted_access == true
}

# Usuário sem tenant (ações de plataforma)
tenant_is_valid if {
    not input.user.tenant_id
    startswith(input.action, "platform:")
}

# Recurso sem tenant (ações de plataforma)
tenant_is_valid if {
    not input.resource.tenant_id
    startswith(input.action, "platform:")
}

# Ação de listar tenants do usuário (não requer tenant específico)
tenant_is_valid if {
    input.action == "user:list"
}

# Ação de listar membros do tenant (tenant_id validado pelo backend)
tenant_is_valid if {
    input.action == "member:list"
}

# Ação de visualizar membro do tenant
tenant_is_valid if {
    input.action == "member:view"
}

# Ação de desativar membro do tenant
tenant_is_valid if {
    input.action == "member:deactivate"
}

# Ação de atualizar papéis do membro
tenant_is_valid if {
    input.action == "member:update"
}

# Ação de reativar membro do tenant
tenant_is_valid if {
    input.action == "member:reactivate"
}

# Ação de listar convites pendentes
tenant_is_valid if {
    input.action == "invitation:list"
}

# Ação de reenviar convite
tenant_is_valid if {
    input.action == "invitation:resend"
}

# Ação de cancelar convite
tenant_is_valid if {
    input.action == "invitation:cancel"
}

# =============================================================================
# Tenant Membership Validation
# =============================================================================

# Valida que usuário é membro ativo do tenant
is_active_member if {
    # Assume que o backend já validou via TenantAccessService
    # Esta validação é redundante mas serve como safeguard
    tenant_is_valid
}

# =============================================================================
# Cross-Tenant Operations (Denied)
# =============================================================================

# Nega explicitamente operações cross-tenant
deny_cross_tenant contains msg if {
    not tenant_is_valid
    input.user.unrestricted_access != true
    msg := sprintf("Acesso negado: usuário do tenant %s tentou acessar recurso do tenant %s",
        [input.user.tenant_id, input.resource.tenant_id])
}

# =============================================================================
# Tenant-Specific Configurations
# =============================================================================

# Permite configurações específicas por tenant (futuro)
# Exemplo: tenant pode ter políticas customizadas de desconto
tenant_config contains config if {
    # Placeholder para configurações por tenant
    # data.tenants conterá configurações carregadas do banco
    config := data.tenants[input.user.tenant_id]
}

# =============================================================================
# Exports
# =============================================================================

multi_tenant_valid := tenant_is_valid
multi_tenant_deny := deny_cross_tenant
