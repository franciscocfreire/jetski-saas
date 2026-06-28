package jetski.authorization_test

import data.jetski.authorization

# =============================================================================
# Regressão de segurança: ações platform:* só para super admin (unrestricted).
# O wildcard "*" do ADMIN_TENANT NÃO pode liberar platform:* (senão qualquer
# admin de empresa aprovaria/listaria todas as empresas).
# =============================================================================

admin_tenant(action) := {
	"action": action,
	"user": {"id": "u", "tenant_id": "11111111-1111-1111-1111-111111111111", "role": "ADMIN_TENANT", "roles": ["ADMIN_TENANT"]},
	"resource": {"tenant_id": "11111111-1111-1111-1111-111111111111"},
}

operador(action) := {
	"action": action,
	"user": {"id": "u", "tenant_id": "11111111-1111-1111-1111-111111111111", "role": "OPERADOR", "roles": ["OPERADOR"]},
	"resource": {"tenant_id": "11111111-1111-1111-1111-111111111111"},
}

super_admin(action) := {
	"action": action,
	"user": {"id": "u", "unrestricted_access": true, "tenant_id": "11111111-1111-1111-1111-111111111111", "role": "PLATFORM_ADMIN", "roles": ["PLATFORM_ADMIN"]},
	"resource": {"tenant_id": "11111111-1111-1111-1111-111111111111"},
}

# ADMIN_TENANT NÃO pode ações de plataforma
test_admin_tenant_denied_platform_tenants if {
	not authorization.allow with input as admin_tenant("platform:tenants")
}

test_admin_tenant_denied_platform_approve if {
	not authorization.allow with input as admin_tenant("platform:approve")
}

test_admin_tenant_denied_platform_suspend if {
	not authorization.allow with input as admin_tenant("platform:suspend")
}

# Outros papéis também negados
test_operador_denied_platform if {
	not authorization.allow with input as operador("platform:tenants")
}

# Super admin PODE ações de plataforma
test_super_admin_allowed_platform_approve if {
	authorization.allow with input as super_admin("platform:approve")
}

test_super_admin_allowed_platform_tenants if {
	authorization.allow with input as super_admin("platform:tenants")
}

# Super admin tem god mode em ações normais (qualquer tenant que selecionar)
test_super_admin_god_mode_normal_action if {
	authorization.allow with input as super_admin("modelo:list")
}

# ADMIN_TENANT continua podendo ações normais do seu tenant (não quebrou nada)
test_admin_tenant_allowed_normal_action if {
	authorization.allow with input as admin_tenant("modelo:list")
}

# Re-cifragem de segredos (rotação de chave): só super admin.
# POST /v1/platform/secrets/reencrypt → action "platform:reencrypt".
test_admin_tenant_denied_platform_reencrypt if {
	not authorization.allow with input as admin_tenant("platform:reencrypt")
}

test_operador_denied_platform_reencrypt if {
	not authorization.allow with input as operador("platform:reencrypt")
}

test_super_admin_allowed_platform_reencrypt if {
	authorization.allow with input as super_admin("platform:reencrypt")
}
