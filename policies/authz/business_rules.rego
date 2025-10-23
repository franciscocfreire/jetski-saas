package jetski.business

import future.keywords.if
import future.keywords.in

# =============================================================================
# Business Rules Policies
# =============================================================================
#
# Implementa regras de negócio específicas do domínio Jetski SaaS.
#
# Regras implementadas:
# - RN06: Jetski em manutenção não pode ser reservado
# - RN: Check-out apenas após check-in
# - RN: Não permite reserva com conflito de horário
# - RN: Combustível registrado apenas se policy != "incluso"
#
# =============================================================================

# =============================================================================
# RN06: Manutenção bloqueia reserva
# =============================================================================

# Nega reserva se jetski está em manutenção
deny_manutencao contains msg if {
    input.action == "reserva:criar"
    jetski := data.jetskis[input.resource.jetski_id]
    jetski.status == "manutencao"
    msg := sprintf("Jetski %s está em manutenção e não pode ser reservado", [input.resource.jetski_id])
}

# Nega reserva se jetski tem OS aberta
deny_manutencao contains msg if {
    input.action == "reserva:criar"
    jetski := data.jetskis[input.resource.jetski_id]
    jetski.manutencoes_abertas > 0
    msg := sprintf("Jetski %s possui manutenções abertas (%d OS)",
        [input.resource.jetski_id, jetski.manutencoes_abertas])
}

# =============================================================================
# RN: Lifecycle de Locação
# =============================================================================

# Check-out requer check-in prévio
deny_lifecycle contains msg if {
    input.action == "locacao:checkout"
    locacao := data.locacoes[input.resource.id]
    not locacao.checkin_realizado
    msg := "Check-out não permitido: check-in não foi realizado"
}

# Não permite check-in duplo
deny_lifecycle contains msg if {
    input.action == "locacao:checkin"
    locacao := data.locacoes[input.resource.id]
    locacao.checkin_realizado
    msg := "Check-in já foi realizado para esta locação"
}

# Não permite checkout duplo
deny_lifecycle contains msg if {
    input.action == "locacao:checkout"
    locacao := data.locacoes[input.resource.id]
    locacao.checkout_realizado
    msg := "Check-out já foi realizado para esta locação"
}

# =============================================================================
# RN: Conflito de Reservas
# =============================================================================

# Nega reserva se há conflito de horário
deny_conflito contains msg if {
    input.action == "reserva:criar"
    reserva_nova := input.operation.reserva
    some reserva_existente in data.reservas

    # Mesmo jetski
    reserva_existente.jetski_id == reserva_nova.jetski_id

    # Sobreposição de horário
    horarios_sobrepoem(reserva_nova, reserva_existente)

    msg := sprintf("Conflito de horário: jetski %s já reservado para %s",
        [reserva_nova.jetski_id, reserva_existente.hora_inicio])
}

# Helper: verifica sobreposição de horários
horarios_sobrepoem(r1, r2) if {
    # r1 começa durante r2
    time.parse_rfc3339_ns(r1.hora_inicio) >= time.parse_rfc3339_ns(r2.hora_inicio)
    time.parse_rfc3339_ns(r1.hora_inicio) < time.parse_rfc3339_ns(r2.hora_fim)
}

horarios_sobrepoem(r1, r2) if {
    # r1 termina durante r2
    time.parse_rfc3339_ns(r1.hora_fim) > time.parse_rfc3339_ns(r2.hora_inicio)
    time.parse_rfc3339_ns(r1.hora_fim) <= time.parse_rfc3339_ns(r2.hora_fim)
}

horarios_sobrepoem(r1, r2) if {
    # r1 engloba r2
    time.parse_rfc3339_ns(r1.hora_inicio) <= time.parse_rfc3339_ns(r2.hora_inicio)
    time.parse_rfc3339_ns(r1.hora_fim) >= time.parse_rfc3339_ns(r2.hora_fim)
}

# =============================================================================
# RN03: Política de Combustível
# =============================================================================

# Permite registrar abastecimento apenas se policy != "incluso"
allow_abastecimento if {
    input.action == "abastecimento:registrar"
    tenant := data.tenants[input.user.tenant_id]
    tenant.fuel_policy != "incluso"
}

# Nega abastecimento se policy == "incluso"
deny_combustivel contains msg if {
    input.action == "abastecimento:registrar"
    tenant := data.tenants[input.user.tenant_id]
    tenant.fuel_policy == "incluso"
    msg := "Combustível está incluído no plano. Não registre separadamente."
}

# =============================================================================
# RN: Fechamento bloqueia edições retroativas
# =============================================================================

# Nega edições em dia já fechado
deny_fechamento contains msg if {
    input.action in ["locacao:update", "abastecimento:update", "desconto:aplicar"]
    resource := data.resources[input.resource.id]

    # Verifica se data do recurso está em período fechado
    data_recurso := resource.data
    some fechamento in data.fechamentos_diarios

    fechamento.tenant_id == input.user.tenant_id
    fechamento.data == data_recurso
    fechamento.status == "fechado"

    msg := sprintf("Operação bloqueada: dia %s já foi fechado", [data_recurso])
}

# =============================================================================
# RN: Fotos obrigatórias (Check-in e Check-out)
# =============================================================================

# Valida quantidade mínima de fotos no check-in
deny_fotos contains msg if {
    input.action == "locacao:checkin"
    fotos := input.operation.fotos
    count(fotos) < 4
    msg := "Check-in requer no mínimo 4 fotos (frontal, traseira, laterais)"
}

# Valida quantidade mínima de fotos no check-out
deny_fotos contains msg if {
    input.action == "locacao:checkout"
    fotos := input.operation.fotos
    count(fotos) < 4
    msg := "Check-out requer no mínimo 4 fotos (frontal, traseira, laterais)"
}

# =============================================================================
# Decisão de Business Rules
# =============================================================================

# Coleta todas as negações
all_denies := deny_manutencao |
              deny_lifecycle |
              deny_conflito |
              deny_combustivel |
              deny_fechamento |
              deny_fotos

# Permite se não há nenhuma negação
allow_business if {
    count(all_denies) == 0
}

# =============================================================================
# Exports
# =============================================================================

business_allow := allow_business
business_deny := all_denies
