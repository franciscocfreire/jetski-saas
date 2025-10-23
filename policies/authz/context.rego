package jetski.context

import future.keywords.if

# Helper functions for array containment
pier_operations := {"locacao:checkin", "locacao:checkout", "abastecimento:registrar"}
fechamento_operations := {"fechamento:diario", "fechamento:mensal"}
mobile_preferred_operations := {"locacao:checkin", "locacao:checkout", "foto:upload"}
checkin_checkout_operations := {"locacao:checkin", "locacao:checkout"}
critical_operations := {"fechamento:mensal", "comissao:aprovar"}

# =============================================================================
# Context-Based Policies
# =============================================================================
#
# Políticas baseadas em atributos de contexto:
# - Horário (horário comercial, hora do dia)
# - Localização (IP, geolocalização)
# - Device (mobile, desktop)
# - Ambiente (produção, staging)
#
# =============================================================================

# =============================================================================
# Horário Comercial
# =============================================================================

# Define horário comercial: 8h às 20h
is_horario_comercial if {
    # Parse timestamp do contexto
    timestamp_ns := time.parse_rfc3339_ns(input.context.timestamp)

    # Extrai hora (0-23)
    [hora, _, _] := time.clock(timestamp_ns)

    # Valida se está entre 8h e 20h
    hora >= 8
    hora < 20
}

# Define final de semana
is_fim_de_semana if {
    timestamp_ns := time.parse_rfc3339_ns(input.context.timestamp)
    weekday := time.weekday(timestamp_ns)
    weekday in ["Saturday", "Sunday"]
}

# =============================================================================
# Restrições por Horário
# =============================================================================

# Operações de pier apenas em horário comercial
deny_horario contains msg if {
    pier_operations[input.action]
    not is_horario_comercial
    msg := "Operações de pier permitidas apenas em horário comercial (8h-20h)"
}

# Fechamento diário apenas após 20h
deny_horario contains msg if {
    input.action == "fechamento:diario"
    timestamp_ns := time.parse_rfc3339_ns(input.context.timestamp)
    [hora, _, _] := time.clock(timestamp_ns)
    hora < 20
    msg := "Fechamento diário permitido apenas após 20h"
}

# Fechamento mensal apenas nos primeiros 5 dias do mês
deny_horario contains msg if {
    input.action == "fechamento:mensal"
    timestamp_ns := time.parse_rfc3339_ns(input.context.timestamp)
    [_, _, dia] := time.date(timestamp_ns)
    dia > 5
    msg := "Fechamento mensal permitido apenas nos primeiros 5 dias do mês"
}

# =============================================================================
# Restrições por IP (Geolocalização)
# =============================================================================

# Lista de IPs permitidos para operações sensíveis (configurável)
allowed_ips_sensitive := [
    "192.168.1.0/24",  # Rede local do pier
    "10.0.0.0/8"       # VPN corporativa
]

# Valida IP para operações de fechamento
deny_ip contains msg if {
    fechamento_operations[input.action]
    not ip_is_allowed(input.context.ip)
    msg := sprintf("Fechamentos permitidos apenas de IPs autorizados. IP: %s", [input.context.ip])
}

# Helper: valida se IP está na lista permitida
ip_is_allowed(ip) if {
    # Simplificado - em produção usar net.cidr_contains
    some allowed in allowed_ips_sensitive
    startswith(ip, split(allowed, "/")[0])
}

# Bypassa validação de IP para platform admins
ip_is_allowed(_ip) if {
    input.user.unrestricted_access == true
}

# =============================================================================
# Restrições por Device
# =============================================================================

# Mobile app pode fazer check-in/out (preferencial)
allow_device_mobile if {
    mobile_preferred_operations[input.action]
    input.context.device == "mobile"
}

# Desktop/Web também pode, mas com warning
allow_device_web if {
    checkin_checkout_operations[input.action]
    input.context.device == "web"
}

# Alerta se check-in/out não é via mobile
warn_device contains msg if {
    checkin_checkout_operations[input.action]
    input.context.device != "mobile"
    msg := "Recomendado usar app mobile para check-in/check-out (qualidade de fotos)"
}

# =============================================================================
# Restrições por Ambiente
# =============================================================================

# Operações críticas apenas em produção
deny_ambiente contains msg if {
    critical_operations[input.action]
    input.context.environment != "production"
    msg := sprintf("Operação %s permitida apenas em ambiente de produção", [input.action])
}

# =============================================================================
# Decisão de Contexto
# =============================================================================

# Coleta todas as negações
all_denies := deny_horario | deny_ip | deny_ambiente

# Permite se não há nenhuma negação
allow_context if {
    count(all_denies) == 0
}

# =============================================================================
# Exports
# =============================================================================

context_allow := allow_context
context_deny := all_denies
context_warnings := warn_device
