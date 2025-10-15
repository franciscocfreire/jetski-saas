# =============================================================================
# JETSKI SaaS - Alçadas Policy (Approval Authority Limits)
# =============================================================================
#
# Esta política implementa alçadas (limites de aprovação) baseadas em:
# - Role do usuário
# - Tipo de operação
# - Valor da operação
# - Contexto temporal
#
# Casos de uso:
# 1. Desconto em locações (OPERADOR: até 5%, GERENTE: até 20%)
# 2. Aprovação de OS de manutenção (por valor)
# 3. Fechamento de caixa (por valor e período)
# 4. Cancelamento de locações (por tempo restante)
#
# =============================================================================

package jetski.authz.alcada

import future.keywords.if
import future.keywords.in

# =============================================================================
# DEFAULTS
# =============================================================================

default allow := false
default requer_aprovacao := false

# =============================================================================
# ALÇADA 1: DESCONTO EM LOCAÇÕES
# =============================================================================

# OPERADOR pode dar até 5% desconto
allow if {
    input.action == "desconto:aplicar"
    input.user.role == "OPERADOR"
    input.operation.percentual_desconto <= 5
    tenant_is_valid
}

# GERENTE pode dar até 20% desconto
allow if {
    input.action == "desconto:aplicar"
    input.user.role == "GERENTE"
    input.operation.percentual_desconto <= 20
    tenant_is_valid
}

# ADMIN_TENANT pode dar qualquer desconto
allow if {
    input.action == "desconto:aplicar"
    input.user.role == "ADMIN_TENANT"
    tenant_is_valid
}

# Desconto acima de 20% requer aprovação de ADMIN_TENANT
requer_aprovacao if {
    input.action == "desconto:aplicar"
    input.operation.percentual_desconto > 20
    input.user.role != "ADMIN_TENANT"
}

# Determinar quem deve aprovar
aprovador_requerido := "ADMIN_TENANT" if {
    input.action == "desconto:aplicar"
    input.operation.percentual_desconto > 20
}

# =============================================================================
# ALÇADA 2: APROVAÇÃO DE ORDEM DE SERVIÇO (MANUTENÇÃO)
# =============================================================================

# MECANICO pode abrir OS até R$ 5.000
allow if {
    input.action == "manutencao:aprovar_os"
    input.user.role == "MECANICO"
    input.operation.valor_os <= 5000
    tenant_is_valid
}

# GERENTE pode aprovar OS de R$ 5.001 até R$ 20.000
allow if {
    input.action == "manutencao:aprovar_os"
    input.user.role == "GERENTE"
    input.operation.valor_os > 5000
    input.operation.valor_os <= 20000
    tenant_is_valid
}

# ADMIN_TENANT pode aprovar qualquer OS
allow if {
    input.action == "manutencao:aprovar_os"
    input.user.role == "ADMIN_TENANT"
    tenant_is_valid
}

# OS acima de R$ 20.000 requer aprovação de ADMIN_TENANT
requer_aprovacao if {
    input.action == "manutencao:aprovar_os"
    input.operation.valor_os > 20000
    input.user.role in ["MECANICO", "GERENTE"]
}

# =============================================================================
# ALÇADA 3: FECHAMENTO DE CAIXA
# =============================================================================

# GERENTE pode fechar caixa diário até R$ 50.000
allow if {
    input.action == "fechamento:diario"
    input.user.role == "GERENTE"
    input.operation.valor_total <= 50000
    tenant_is_valid
}

# FINANCEIRO pode fechar qualquer valor (diário ou mensal)
allow if {
    input.action in ["fechamento:diario", "fechamento:mensal"]
    input.user.role == "FINANCEIRO"
    tenant_is_valid
}

# ADMIN_TENANT pode reabrir fechamento (auditoria)
allow if {
    input.action == "fechamento:reabrir"
    input.user.role == "ADMIN_TENANT"
    tenant_is_valid
}

# =============================================================================
# ALÇADA 4: CANCELAMENTO DE LOCAÇÃO (baseado em tempo)
# =============================================================================

# OPERADOR pode cancelar até 1h antes do início
allow if {
    input.action == "locacao:cancelar"
    input.user.role == "OPERADOR"
    horas_ate_inicio >= 1
    tenant_is_valid
}

# GERENTE pode cancelar até o início da locação
allow if {
    input.action == "locacao:cancelar"
    input.user.role == "GERENTE"
    horas_ate_inicio >= 0
    tenant_is_valid
}

# ADMIN_TENANT pode cancelar locação em andamento (com justificativa)
allow if {
    input.action == "locacao:cancelar"
    input.user.role == "ADMIN_TENANT"
    input.operation.justificativa
    tenant_is_valid
}

# Calcular horas até início da locação
horas_ate_inicio := diff_horas if {
    # timestamp_inicio vem em Unix nanoseconds
    inicio_ns := input.operation.locacao_inicio_timestamp
    agora_ns := time.now_ns()

    # Converter para horas
    diff_ns := inicio_ns - agora_ns
    diff_horas := diff_ns / (1000000000 * 3600)  # ns -> horas
}

# =============================================================================
# HELPERS
# =============================================================================

# Validação multi-tenant (compartilhado)
tenant_is_valid if {
    input.user.tenant_id
    input.resource.tenant_id
    input.user.tenant_id == input.resource.tenant_id
}

# =============================================================================
# METADATA PARA AUDITORIA
# =============================================================================

decision := {
    "allow": allow,
    "requer_aprovacao": requer_aprovacao,
    "aprovador_requerido": aprovador_requerido,
    "tenant_valid": tenant_is_valid,
    "user_id": input.user.id,
    "user_role": input.user.role,
    "action": input.action,
    "valor": object.get(input.operation, "valor_os",
               object.get(input.operation, "valor_total",
               object.get(input.operation, "percentual_desconto", 0))),
    "timestamp": time.now_ns()
}
