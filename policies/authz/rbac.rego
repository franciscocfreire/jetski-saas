package jetski.rbac

import future.keywords.contains
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
        "reserva:confirmar",
        # Balcão (atendimento assistido) + validação de pagamento
        "reserva:create",
        "reserva:confirmar-sinal",
        "reserva:recusar-pagamento",
        "reserva:registrar-pagamento",
        "reserva:no-show",
        "reserva:extrato",
        "locacao:registrar-pagamento",
        "locacao:extrato",
        "reserva:pagamentos-pendentes",
        "reserva:comprovantes",
        "reserva:emitir-documentos",
        "reserva:habilitacao",
        "reserva:aceite",
        "cliente:create",
        "cliente:claim",
        "cliente:reenviar",
        "item-opcional:list",
        "item-opcional:view",
        "instrutor:list",
        "instrutor:view",
        "documento:list",
        "documento:view",
        "documento:download",
        "documento:reenviar",
        "documento:imagem-config",  # leitura da config de compressão de imagem (upload)
        "gru:list",   # Módulo GRUs (ciclo Marinha)
        "gru:view",
        # Emissão delegada (V048): staff que emite escolhe o instrutor da EAMA parceira
        "vinculo-emissao:instrutores-parceiro"
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
        "instrutor:*",  # Cadastro de instrutores (EAMA) — Anexo 5-B-1
        "documento:*",  # Consulta de documentos emitidos
        "gru:*",  # Módulo GRUs (ciclo Marinha)
        # Emissão delegada (V048): consulta da parceria + painel do emissor;
        # gestão do vínculo (convidar/aceitar/bloquear/revogar) é só ADMIN_TENANT
        "vinculo-emissao:list",
        "vinculo-emissao:termo",
        "vinculo-emissao:instrutores-parceiro",
        "vinculo-emissao:instrutores-designados",  # EAMA designa quem atende cada operadora (V049)
        "emissao-delegada:*",
        "item-opcional:*",  # Itens opcionais (coletes, equipamentos, etc)

        # Permissões específicas de GERENTE
        "desconto:aplicar",
        "desconto:aprovar",
        "os:*",          # Todas as operações de OS (create, view, list, aprovar, etc)
        "manutencao:*",  # Alias para os:* (manutenção)
        "fechamento:*",  # Todas as operações de fechamento
        "comissao:*",    # Todas as operações de comissão (view, list, aprovar, create)
        "pagamento:*",   # Pagamento de vendedores (view, list, registrar)
        "politica-comissao:*",
        "politicas-comissao:*",
        "config:*",      # Configurações do tenant (comissões, bônus, etc)
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
        "reserva:view",
        "reserva:list",
        "reserva:pagamentos-pendentes",
        "reserva:comprovantes",
        "reserva:confirmar-sinal",
        "reserva:recusar-pagamento",
        "reserva:registrar-pagamento",
        "reserva:registrar-estorno",
        "reserva:extrato",
        "locacao:registrar-pagamento",
        "locacao:extrato",
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
        "pagamento:*",   # Pagamento de vendedores (full access)
        "politica-comissao:list",
        "politica-comissao:view",
        "politica-comissao:create",
        "relatorio:financeiro",
        "relatorio:comissoes",
        "vendedor:view",
        "vendedor:list",
        "cliente:view",
        "cliente:list",
        # Validação de pagamento (sinal/total) — fila do Financeiro
        "reserva:view",
        "reserva:list",
        "reserva:confirmar-sinal",
        "reserva:recusar-pagamento",
        "reserva:pagamentos-pendentes",
        "reserva:comprovantes",
        "documento:list",
        "documento:view",
        "documento:download",
        "documento:reenviar",
        "gru:list",   # Módulo GRUs (ciclo Marinha)
        "gru:view"
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
        # NÃO confirma sinal: separação de funções — quem cria/recebe o
        # pagamento (vendedor, com conflito de comissão) não valida o sinal.
        # A validação é staff (OPERADOR/GERENTE/FINANCEIRO).
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
# Permissões efetivas (menu do backoffice / tela de permissões)
# =============================================================================

# União das permissões CRUAS (com wildcards "*" e "recurso:*") dos roles do
# input. Consumida por GET /v1/user/permissions via data API:
#   POST /v1/data/jetski/rbac/user_permissions
#   { "input": { "user": { "roles": ["OPERADOR", ...] } } }
# O cliente aplica o mesmo matching de action_matches_permission.
user_permissions contains permission if {
    some role in input.user.roles
    some permission in role_permissions[role]
}

# =============================================================================
# Exports
# =============================================================================

# Exporta decisões para o pacote principal
rbac_allow := allow_rbac
platform_allow := allow_platform
