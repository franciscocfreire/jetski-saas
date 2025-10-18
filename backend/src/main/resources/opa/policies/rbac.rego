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
        "reserva:list"
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

        # Permissões específicas de GERENTE
        "desconto:aplicar",
        "desconto:aprovar",
        "os:view",
        "os:aprovar",
        "fechamento:diario",
        "relatorio:operacional",
        "vendedor:view",
        "vendedor:list"
    ],

    "FINANCEIRO": [
        "locacao:view",
        "locacao:list",
        "fechamento:mensal",
        "fechamento:diario:view",
        "comissao:calcular",
        "comissao:aprovar",
        "comissao:view",
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
allow_rbac if {
    user_role := input.user.role
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
# Exports
# =============================================================================

# Exporta decisões para o pacote principal
rbac_allow := allow_rbac
platform_allow := allow_platform
