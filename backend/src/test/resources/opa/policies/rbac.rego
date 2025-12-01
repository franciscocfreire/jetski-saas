package jetski.rbac

import future.keywords.if
import future.keywords.in

# =============================================================================
# RBAC (Role-Based Access Control) Policies
# =============================================================================
#
# Define permissões baseadas em roles para o sistema Jetski SaaS.
# Cada role tem um conjunto de actions permitidas.
#
# Roles disponíveis:
# - OPERADOR: Operações do pier (check-in/out, abastecimento, fotos)
# - GERENTE: Gestão operacional (descontos, OS, fechamentos diários)
# - FINANCEIRO: Gestão financeira (comissões, fechamentos mensais)
# - MECANICO: Manutenção (criar/fechar OS)
# - VENDEDOR: Vendas (criar reservas, ganhar comissão)
# - ADMIN_TENANT: Administrador do tenant (acesso total ao tenant)
#
# =============================================================================

# Mapeamento de permissões por role
role_permissions := {
    "OPERADOR": [
        "locacao:list",
        "locacao:view",
        "locacao:checkin",
        "locacao:checkout",
        "abastecimento:registrar",
        "abastecimento:view",
        "foto:upload",
        "foto:view",
        "jetski:view",
        "jetski:list",
        "modelo:view",
        "modelo:list",
        "cliente:view",
        "cliente:list",
        "reserva:view",
        "reserva:list",
        "item-opcional:view",
        "item-opcional:list"
    ],

    "GERENTE": [
        # Todas as permissões de OPERADOR
        "locacao:*",
        "abastecimento:*",
        "foto:*",
        "jetski:*",
        "modelo:*",
        "cliente:*",
        "reserva:*",
        "item-opcional:*",

        # Permissões específicas de GERENTE
        "desconto:aplicar",
        "desconto:aprovar",
        "os:view",
        "os:aprovar",
        "fechamento:diario",
        "fechamento:consolidar",  # Consolidar fechamento diário
        "relatorio:operacional",
        "vendedor:view",
        "vendedor:list",
        "member:list",
        "member:view",
        "member:deactivate"
    ],

    "FINANCEIRO": [
        "locacao:view",
        "locacao:list",
        "fechamento:mensal",
        "fechamento:diario",
        "fechamento:diario:view",
        "comissao:calcular",
        "comissao:aprovar",
        "comissao:view",
        "comissao:list",
        "relatorio:financeiro",
        "relatorio:comissoes",
        "vendedor:view",
        "vendedor:list",
        "cliente:view",
        "cliente:list"
    ],

    "MECANICO": [
        "jetski:view",
        "jetski:list",
        "os:criar",
        "os:fechar",
        "os:view",
        "os:list",
        "os:update",
        "abastecimento:view",
        "foto:upload",
        "foto:view"
    ],

    "VENDEDOR": [
        "reserva:criar",
        "reserva:view",
        "reserva:list",
        "reserva:update",
        "cliente:criar",
        "cliente:view",
        "cliente:list",
        "cliente:update",
        "modelo:view",
        "modelo:list",
        "jetski:view",
        "jetski:list",
        "comissao:view:own"  # Apenas suas próprias comissões
    ],

    "ADMIN_TENANT": [
        "*"  # Acesso total ao tenant
    ]
}

# Permissões de plataforma (super admin)
platform_permissions := [
    "tenant:*",
    "plano:*",
    "assinatura:*",
    "usuario:*",
    "membro:*",
    "platform:*"
]

# =============================================================================
# RBAC Authorization Rules
# =============================================================================

# Verifica se ação corresponde a uma permissão
action_matches_permission(action, permission) if {
    permission == "*"
}

action_matches_permission(action, permission) if {
    permission == action
}

action_matches_permission(action, permission) if {
    # Wildcard pattern: "locacao:*" matches "locacao:checkin"
    parts_permission := split(permission, ":")
    parts_action := split(action, ":")

    count(parts_permission) == 2
    count(parts_action) == 2

    parts_permission[0] == parts_action[0]
    parts_permission[1] == "*"
}

# Usuário tem permissão baseada em sua role
has_permission(user_role, action) if {
    permissions := role_permissions[user_role]
    some permission in permissions
    action_matches_permission(action, permission)
}

# ADMIN_TENANT tem acesso total (wildcard)
has_permission("ADMIN_TENANT", _action) := true

# Regra principal: permite acesso se usuário tem a permissão
# Verifica role único (backward compatibility)
allow_rbac if {
    user_role := input.user.role
    action := input.action
    has_permission(user_role, action)
}

# Verifica array de roles (modo multi-role)
allow_rbac if {
    user_role := input.user.roles[_]
    action := input.action
    has_permission(user_role, action)
}

# =============================================================================
# Platform Admin (Unrestricted Access)
# =============================================================================

# Usuário com unrestricted_access=true (platform admin)
is_platform_admin if {
    input.user.unrestricted_access == true
}

# Platform admin bypassa RBAC para recursos de plataforma
allow_platform if {
    is_platform_admin
    startswith(input.action, "tenant:")
}

allow_platform if {
    is_platform_admin
    startswith(input.action, "plano:")
}

allow_platform if {
    is_platform_admin
    startswith(input.action, "assinatura:")
}

allow_platform if {
    is_platform_admin
    startswith(input.action, "platform:")
}

# =============================================================================
# Deny Messages
# =============================================================================

# Gera mensagem de negação quando RBAC falha
rbac_deny contains msg if {
    not allow_rbac
    not allow_platform
    user_role := input.user.role
    action := input.action
    msg := sprintf("RBAC: role '%s' não tem permissão para '%s'", [user_role, action])
}

# Se role não existe
rbac_deny contains msg if {
    not allow_rbac
    not allow_platform
    not input.user.role
    msg := "RBAC: usuário não possui role definida"
}

# =============================================================================
# Exports
# =============================================================================

# Exporta decisões para o pacote principal
default rbac_allow := false
rbac_allow := allow_rbac

default platform_allow := false
platform_allow := allow_platform
