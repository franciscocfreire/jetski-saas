package jetski.context

import future.keywords.if
import future.keywords.in

# Testes para Context Policies (políticas baseadas em contexto: tempo, IP, device)
# Executar com: opa test -v src/main/resources/opa/policies/

# ==================== Horário Comercial Tests ====================

test_horario_comercial_9am if {
    is_horario_comercial with input as {
        "context": {"timestamp": "2025-01-15T09:00:00Z"}
    }
}

test_horario_comercial_12pm if {
    is_horario_comercial with input as {
        "context": {"timestamp": "2025-01-15T12:00:00Z"}
    }
}

test_horario_comercial_7pm if {
    is_horario_comercial with input as {
        "context": {"timestamp": "2025-01-15T19:00:00Z"}
    }
}

test_not_horario_comercial_7am if {
    not is_horario_comercial with input as {
        "context": {"timestamp": "2025-01-15T07:00:00Z"}
    }
}

test_not_horario_comercial_8pm if {
    not is_horario_comercial with input as {
        "context": {"timestamp": "2025-01-15T20:00:00Z"}
    }
}

test_not_horario_comercial_midnight if {
    not is_horario_comercial with input as {
        "context": {"timestamp": "2025-01-15T00:00:00Z"}
    }
}

# ==================== Deny Horário Tests ====================

test_checkin_denied_outside_business_hours if {
    count(deny_horario) > 0 with input as {
        "action": "locacao:checkin",
        "context": {"timestamp": "2025-01-15T07:00:00Z"}
    }
}

test_checkout_denied_outside_business_hours if {
    count(deny_horario) > 0 with input as {
        "action": "locacao:checkout",
        "context": {"timestamp": "2025-01-15T21:00:00Z"}
    }
}

test_checkin_allowed_during_business_hours if {
    count(deny_horario) == 0 with input as {
        "action": "locacao:checkin",
        "context": {"timestamp": "2025-01-15T10:00:00Z"}
    }
}

test_checkout_allowed_during_business_hours if {
    count(deny_horario) == 0 with input as {
        "action": "locacao:checkout",
        "context": {"timestamp": "2025-01-15T18:00:00Z"}
    }
}

test_other_actions_allowed_outside_business_hours if {
    count(deny_horario) == 0 with input as {
        "action": "locacao:view",
        "context": {"timestamp": "2025-01-15T23:00:00Z"}
    }
}

# ==================== Dia Útil Tests ====================

test_is_weekday_monday if {
    is_dia_util with input as {
        "context": {"timestamp": "2025-01-20T10:00:00Z"} # Monday
    }
}

test_is_weekday_friday if {
    is_dia_util with input as {
        "context": {"timestamp": "2025-01-24T10:00:00Z"} # Friday
    }
}

test_not_weekday_saturday if {
    not is_dia_util with input as {
        "context": {"timestamp": "2025-01-18T10:00:00Z"} # Saturday
    }
}

test_not_weekday_sunday if {
    not is_dia_util with input as {
        "context": {"timestamp": "2025-01-19T10:00:00Z"} # Sunday
    }
}

# ==================== Fechamento Diário Restrictions ====================

test_fechamento_denied_on_weekend if {
    count(deny_horario) > 0 with input as {
        "action": "fechamento:diario",
        "context": {"timestamp": "2025-01-18T10:00:00Z"} # Saturday
    }
}

test_fechamento_allowed_on_weekday if {
    count(deny_horario) == 0 with input as {
        "action": "fechamento:diario",
        "context": {"timestamp": "2025-01-20T10:00:00Z"} # Monday
    }
}

# ==================== IP Whitelist/Blacklist Tests ====================

test_ip_whitelisted if {
    is_ip_allowed with input as {
        "context": {"ip": "192.168.1.100"}
    } with data.ip_whitelist as ["192.168.1.0/24"]
}

test_ip_not_whitelisted if {
    not is_ip_allowed with input as {
        "context": {"ip": "10.0.0.5"}
    } with data.ip_whitelist as ["192.168.1.0/24"]
}

test_ip_blacklisted if {
    not is_ip_allowed with input as {
        "context": {"ip": "203.0.113.50"}
    } with data.ip_blacklist as ["203.0.113.0/24"]
}

test_ip_allowed_when_no_lists if {
    is_ip_allowed with input as {
        "context": {"ip": "1.2.3.4"}
    }
}

# ==================== Device Type Detection ====================

test_detect_mobile_iphone if {
    is_mobile with input as {
        "context": {
            "user_agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X)"
        }
    }
}

test_detect_mobile_android if {
    is_mobile with input as {
        "context": {
            "user_agent": "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36"
        }
    }
}

test_detect_desktop if {
    not is_mobile with input as {
        "context": {
            "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        }
    }
}

test_detect_tablet_ipad if {
    is_mobile with input as {
        "context": {
            "user_agent": "Mozilla/5.0 (iPad; CPU OS 14_0 like Mac OS X)"
        }
    }
}

# ==================== High-Value Actions from Mobile Warning ====================

test_warning_high_value_from_mobile if {
    count(warnings) > 0 with input as {
        "action": "fechamento:mensal",
        "context": {
            "user_agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X)"
        }
    }
}

test_no_warning_high_value_from_desktop if {
    count(warnings) == 0 with input as {
        "action": "fechamento:mensal",
        "context": {
            "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        }
    }
}

test_no_warning_normal_action_from_mobile if {
    count(warnings) == 0 with input as {
        "action": "locacao:view",
        "context": {
            "user_agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X)"
        }
    }
}

# ==================== Environment-Based Restrictions ====================

test_production_allows_all_actions if {
    count(deny_environment) == 0 with input as {
        "action": "locacao:delete",
        "context": {"environment": "production"}
    }
}

test_dev_allows_delete if {
    count(deny_environment) == 0 with input as {
        "action": "locacao:delete",
        "context": {"environment": "development"}
    }
}

test_staging_allows_delete if {
    count(deny_environment) == 0 with input as {
        "action": "locacao:delete",
        "context": {"environment": "staging"}
    }
}

# Note: Current policy doesn't restrict by environment, but tests show extensibility

# ==================== Rate Limiting Context (future) ====================

# These tests show potential future rate-limiting based on context
# Current implementation doesn't enforce, but OPA could check data.rate_limits

test_context_includes_request_id if {
    # Just validates that context can include request_id for tracing
    input.context.extra.request_id == "req-12345" with input as {
        "context": {
            "extra": {"request_id": "req-12345"}
        }
    }
}

test_context_includes_tenant_config if {
    # Validates tenant-specific config in context
    input.context.extra.tenant_timezone == "America/Sao_Paulo" with input as {
        "context": {
            "extra": {"tenant_timezone": "America/Sao_Paulo"}
        }
    }
}

# ==================== Missing Context Handling ====================

test_missing_timestamp_fails_safe if {
    # When timestamp is missing, time-based rules should fail-safe (allow)
    count(deny_horario) == 0 with input as {
        "action": "locacao:checkin",
        "context": {}
    }
}

test_missing_ip_allows if {
    is_ip_allowed with input as {
        "context": {}
    }
}

test_missing_user_agent_not_mobile if {
    not is_mobile with input as {
        "context": {}
    }
}

# ==================== Combined Context Rules ====================

test_all_context_rules_pass if {
    allow with input as {
        "action": "locacao:checkin",
        "user": {"role": "OPERADOR"},
        "context": {
            "timestamp": "2025-01-20T10:00:00Z", # Monday 10am
            "ip": "192.168.1.100",
            "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            "environment": "production"
        }
    }
}

test_context_deny_blocks_rbac_allow if {
    not allow with input as {
        "action": "locacao:checkin",
        "user": {"role": "OPERADOR"}, # RBAC allows
        "context": {
            "timestamp": "2025-01-20T07:00:00Z", # Outside business hours
            "ip": "192.168.1.100"
        }
    }
}

# ==================== Timezone Handling ====================

test_timestamp_utc_format if {
    is_horario_comercial with input as {
        "context": {"timestamp": "2025-01-15T12:00:00Z"}
    }
}

test_timestamp_with_offset if {
    is_horario_comercial with input as {
        "context": {"timestamp": "2025-01-15T09:00:00-03:00"} # 12:00 UTC
    }
}

# ==================== Edge Cases ====================

test_boundary_8am_is_commercial if {
    is_horario_comercial with input as {
        "context": {"timestamp": "2025-01-15T08:00:00Z"}
    }
}

test_boundary_7_59am_not_commercial if {
    not is_horario_comercial with input as {
        "context": {"timestamp": "2025-01-15T07:59:00Z"}
    }
}

test_boundary_8pm_not_commercial if {
    not is_horario_comercial with input as {
        "context": {"timestamp": "2025-01-15T20:00:00Z"}
    }
}

test_boundary_7_59pm_is_commercial if {
    is_horario_comercial with input as {
        "context": {"timestamp": "2025-01-15T19:59:00Z"}
    }
}

test_empty_context_allows_all if {
    # Empty context should not block requests (fail-safe)
    count(deny_horario) == 0 with input as {
        "action": "locacao:checkin",
        "context": {}
    }

    count(deny_environment) == 0 with input as {
        "action": "locacao:delete",
        "context": {}
    }
}
