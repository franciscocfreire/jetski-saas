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
# - MECANICO: Manutenção (create/fechar OS)
# - VENDEDOR: Vendas (create reservas, ganhar comissão)
# - ADMIN_TENANT: Administrador do tenant (acesso total ao tenant)
#
# =============================================================================

# Mapeamento de permissões por role
role_permissions := {
    "OPERADOR": [
        "locacao:list",
        "locacao:view",
        "locacao:create",
        "locacao:checkin",
        "locacao:checkout",
        "abastecimento:registrar",
        "abastecimento:view",
        "foto:upload",
        "foto:view",
        "jetski:view",
        "jetski:list",
        "jetski:update",
        "modelo:view",
        "modelo:list",
        "cliente:view",
        "cliente:list",
        "cliente:update",
        "cliente:accept-terms",
        "reserva:view",
        "reserva:list",
        "reserva:alocar-jetski",
        "item-opcional:list",
        "item-opcional:view"
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
        "item-opcional:*",  # Itens opcionais (coletes, equipamentos, etc)

        # Permissões específicas de GERENTE
        "desconto:aplicar",
        "desconto:aprovar",
        "os:*",          # Todas as operações de OS (create, view, list, aprovar, etc)
        "manutencao:*",  # Alias para os:* (manutenção)
        "fechamento:*",  # Todas as operações de fechamento
        "comissao:*",    # Todas as operações de comissão (view, list, aprovar, create)
        "politica-comissao:*",
        "politicas-comissao:*",
        "frota:*",       # Dashboard de frota e KPIs
        "relatorio:operacional",
        "relatorio:comissoes",
        "vendedor:view",
        "vendedor:list",
        "member:list",
        "member:view",
        "member:update",
        "member:deactivate",
        "member:reactivate",
        "invitation:list",
        "invitation:resend",
        "invitation:cancel"
    ],

    "FINANCEIRO": [
        "locacao:view",
        "locacao:list",
        "fechamento:mensal",
        "fechamento:consolidar",
        "fechamento:fechar",
        "fechamento:aprovar",
        "fechamento:reabrir",
        "fechamento:view",
        "fechamento:list",
        "comissao:calcular",
        "comissao:aprovar",
        "comissao:pagar",
        "comissao:view",
        "comissao:list",
        "politica-comissao:list",
        "politica-comissao:view",
        "politica-comissao:create",
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
        "os:create",
        "os:start",
        "os:finish",
        "os:fechar",
        "os:view",
        "os:list",
        "os:update",
        "manutencao:create",
        "manutencao:start",
        "manutencao:finish",
        "manutencao:view",
        "manutencao:list",
        "manutencao:update",
        "abastecimento:view",
        "foto:upload",
        "foto:view"
    ],

    "VENDEDOR": [
        "reserva:create",
        "reserva:view",
        "reserva:list",
        "reserva:update",
        "reserva:confirmar-sinal",
        "cliente:create",
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
has_permission("ADMIN_TENANT", _action) if {
    true
}

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
# Exports
# =============================================================================

# Exporta decisões para o pacote principal
rbac_allow := allow_rbac
platform_allow := allow_platform
