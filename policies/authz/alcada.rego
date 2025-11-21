package jetski.alcada

import future.keywords.if
import future.keywords.in

# =============================================================================
# Alçada (Approval Authority) Policies
# =============================================================================
#
# Define regras de alçada para operações que exigem autorização baseada em:
# - Percentual de desconto
# - Valor de ordem de serviço (OS)
# - Valor de transação
# - Nível do usuário
#
# Hierarquia de aprovação:
# 1. OPERADOR: Descontos até 10%, OS até R$ 2.000
# 2. GERENTE: Descontos até 25%, OS até R$ 10.000
# 3. ADMIN_TENANT: Descontos até 50%, OS sem limite
#
# =============================================================================

# =============================================================================
# Alçada de Desconto
# =============================================================================

# OPERADOR pode aplicar desconto até 10%
allow_desconto_operador if {
    input.user.role == "OPERADOR"
    input.action == "desconto:aplicar"
    input.operation.percentual_desconto <= 10
}

# GERENTE pode aplicar desconto até 25%
allow_desconto_gerente if {
    input.user.role == "GERENTE"
    input.action == "desconto:aplicar"
    input.operation.percentual_desconto <= 25
}

# ADMIN_TENANT pode aplicar desconto até 50%
allow_desconto_admin if {
    input.user.role == "ADMIN_TENANT"
    input.action == "desconto:aplicar"
    input.operation.percentual_desconto <= 50
}

# Decisão final de desconto
allow_desconto if allow_desconto_operador
allow_desconto if allow_desconto_gerente
allow_desconto if allow_desconto_admin

# =============================================================================
# Alçada de Ordem de Serviço (Manutenção)
# =============================================================================

# MECANICO pode criar qualquer OS
allow_os_criar if {
    input.user.role == "MECANICO"
    input.action == "os:create"
}

# GERENTE pode criar qualquer OS
allow_os_criar if {
    input.user.role == "GERENTE"
    input.action == "os:create"
}

# OPERADOR pode aprovar OS até R$ 2.000
allow_os_aprovar_operador if {
    input.user.role == "OPERADOR"
    input.action == "os:aprovar"
    input.operation.valor_os <= 2000
}

# GERENTE pode aprovar OS até R$ 10.000
allow_os_aprovar_gerente if {
    input.user.role == "GERENTE"
    input.action == "os:aprovar"
    input.operation.valor_os <= 10000
}

# ADMIN_TENANT pode aprovar qualquer OS
allow_os_aprovar_admin if {
    input.user.role == "ADMIN_TENANT"
    input.action == "os:aprovar"
}

# Decisão final de OS
allow_os if allow_os_criar
allow_os if allow_os_aprovar_operador
allow_os if allow_os_aprovar_gerente
allow_os if allow_os_aprovar_admin

# =============================================================================
# Alçada de Fechamento
# =============================================================================

# GERENTE pode fazer fechamento diário
allow_fechamento_diario if {
    input.user.role == "GERENTE"
    input.action == "fechamento:diario"
}

# ADMIN_TENANT pode fazer fechamento diário
allow_fechamento_diario if {
    input.user.role == "ADMIN_TENANT"
    input.action == "fechamento:diario"
}

# FINANCEIRO pode fazer fechamento mensal
allow_fechamento_mensal if {
    input.user.role == "FINANCEIRO"
    input.action == "fechamento:mensal"
}

# ADMIN_TENANT pode fazer fechamento mensal
allow_fechamento_mensal if {
    input.user.role == "ADMIN_TENANT"
    input.action == "fechamento:mensal"
}

allow_fechamento if allow_fechamento_diario
allow_fechamento if allow_fechamento_mensal

# =============================================================================
# Determina Aprovador Necessário
# =============================================================================

# Se OPERADOR tenta desconto > 10% e <= 25%, precisa de GERENTE
aprovador_requerido := "GERENTE" if {
    input.user.role == "OPERADOR"
    input.action == "desconto:aplicar"
    input.operation.percentual_desconto > 10
    input.operation.percentual_desconto <= 25
}

# Se OPERADOR tenta desconto > 25%, precisa de ADMIN_TENANT
aprovador_requerido := "ADMIN_TENANT" if {
    input.user.role == "OPERADOR"
    input.action == "desconto:aplicar"
    input.operation.percentual_desconto > 25
    input.operation.percentual_desconto <= 50
}

# Se GERENTE tenta desconto > 25%, precisa de ADMIN_TENANT
aprovador_requerido := "ADMIN_TENANT" if {
    input.user.role == "GERENTE"
    input.action == "desconto:aplicar"
    input.operation.percentual_desconto > 25
    input.operation.percentual_desconto <= 50
}

# Se OPERADOR tenta OS > R$ 2.000 e <= R$ 10.000, precisa de GERENTE
aprovador_requerido := "GERENTE" if {
    input.user.role == "OPERADOR"
    input.action == "os:aprovar"
    input.operation.valor_os > 2000
    input.operation.valor_os <= 10000
}

# Se OPERADOR tenta OS > R$ 10.000, precisa de ADMIN_TENANT
aprovador_requerido := "ADMIN_TENANT" if {
    input.user.role == "OPERADOR"
    input.action == "os:aprovar"
    input.operation.valor_os > 10000
}

# Se GERENTE tenta OS > R$ 10.000, precisa de ADMIN_TENANT
aprovador_requerido := "ADMIN_TENANT" if {
    input.user.role == "GERENTE"
    input.action == "os:aprovar"
    input.operation.valor_os > 10000
}

# =============================================================================
# Decisão de Alçada
# =============================================================================

# Ações que NÃO requerem validação de alçada (operações de leitura e gestão)
actions_no_alcada := {
    # Modelos
    "modelo:list", "modelo:view", "modelo:create", "modelo:update", "modelo:delete",

    # Jetskis
    "jetski:list", "jetski:view", "jetski:create", "jetski:update", "jetski:delete",

    # Clientes
    "cliente:list", "cliente:view", "cliente:create", "cliente:update", "cliente:delete",
    "cliente:accept-terms",

    # Reservas
    "reserva:list", "reserva:view", "reserva:create", "reserva:update", "reserva:delete",
    "reserva:confirmar-sinal", "reserva:confirmar", "reserva:alocar-jetski",

    # Locações
    "locacao:list", "locacao:view", "locacao:create",
    "locacao:checkin", "locacao:checkout",

    # Abastecimento
    "abastecimento:view", "abastecimento:registrar",

    # Fotos
    "foto:view", "foto:upload",

    # Vendedores
    "vendedor:list", "vendedor:view", "vendedor:create", "vendedor:update", "vendedor:delete",

    # Ordem de Serviço
    "os:create", "os:view", "os:list", "os:update", "os:fechar",

    # Members
    "member:list", "member:view", "member:deactivate",

    # Relatórios e comissões
    "relatorio:operacional", "relatorio:financeiro", "relatorio:comissoes",
    "comissao:view", "comissao:calcular", "comissao:aprovar", "comissao:pagar",

    # Fechamentos (todas as operações)
    "fechamento:consolidar", "fechamento:fechar", "fechamento:aprovar", "fechamento:reabrir",
    "fechamento:diario", "fechamento:mensal", "fechamento:view", "fechamento:list",

    # Políticas de comissão
    "politica-comissao:list", "politica-comissao:view", "politica-comissao:create",

    # User management
    "user:list"
}

# Permite se é uma ação que não requer alçada
default allow_alcada := false

allow_alcada if {
    actions_no_alcada[input.action]
}

# Permite se passou na alçada de desconto
allow_alcada if allow_desconto

# Permite se passou na alçada de OS
allow_alcada if allow_os

# Permite se passou na alçada de fechamento
allow_alcada if allow_fechamento

# Requer aprovação se tentou mas não tem alçada
default requer_aprovacao := false

requer_aprovacao if {
    not allow_alcada
    aprovador_requerido  # Existe um aprovador definido
}

# =============================================================================
# Exports
# =============================================================================

alcada_allow := allow_alcada
alcada_requer_aprovacao := requer_aprovacao

default alcada_aprovador_requerido := null
alcada_aprovador_requerido := aprovador_requerido if {
    aprovador_requerido
}
