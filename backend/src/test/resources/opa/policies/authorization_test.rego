package jetski.authorization

import data.jetski.rbac
import data.jetski.alcada
import data.jetski.multi_tenant
import data.jetski.business_rules
import data.jetski.context

# Testes para Authorization Policy (política principal que integra todas)
# Executar com: opa test -v src/test/resources/opa/policies/

# ==================== Complete Authorization Flow ====================

test_complete_authorization_success if {
    result.allow == true with input as {
        "action": "locacao:checkin",
        "user": {
            "id": "operador@test.com",
            "tenant_id": "tenant-abc",
            "role": "OPERADOR"
        },
        "resource": {
            "id": "locacao-123",
            "tenant_id": "tenant-abc",
            "jetski_id": "jetski-456"
        },
        "context": {
            "timestamp": "2025-01-20T10:00:00Z", # Monday 10am
            "ip": "192.168.1.100"
        }
    } with data.jetskis as {
        "jetski-456": {"status": "disponivel"}
    }
}

test_complete_authorization_rbac_deny if {
    result.allow == false with input as {
        "action": "fechamento:mensal",
        "user": {
            "id": "operador@test.com",
            "tenant_id": "tenant-abc",
            "role": "OPERADOR" # OPERADOR não pode fechar mês
        },
        "resource": {
            "id": "fechamento-123",
            "tenant_id": "tenant-abc"
        },
        "context": {
            "timestamp": "2025-01-20T10:00:00Z"
        }
    }
}

test_complete_authorization_tenant_deny if {
    result.allow == false
    result.tenant_is_valid == false
    with input as {
        "action": "locacao:view",
        "user": {
            "id": "operador@test.com",
            "tenant_id": "tenant-abc",
            "role": "OPERADOR"
        },
        "resource": {
            "id": "locacao-123",
            "tenant_id": "tenant-xyz" # Different tenant
        },
        "context": {
            "timestamp": "2025-01-20T10:00:00Z"
        }
    }
}

test_complete_authorization_context_deny if {
    result.allow == false with input as {
        "action": "locacao:checkin",
        "user": {
            "id": "operador@test.com",
            "tenant_id": "tenant-abc",
            "role": "OPERADOR"
        },
        "resource": {
            "id": "locacao-123",
            "tenant_id": "tenant-abc"
        },
        "context": {
            "timestamp": "2025-01-20T07:00:00Z" # Outside business hours
        }
    }
}

# ==================== Alçada Integration ====================

test_authorization_alcada_requires_approval if {
    result.allow == false
    result.requer_aprovacao == true
    result.aprovador_requerido == "GERENTE"
    with input as {
        "action": "desconto:aplicar",
        "user": {
            "id": "operador@test.com",
            "tenant_id": "tenant-abc",
            "role": "OPERADOR"
        },
        "resource": {
            "id": "locacao-123",
            "tenant_id": "tenant-abc"
        },
        "operation": {
            "percentual_desconto": 15 # Exceeds OPERADOR limit (10%)
        },
        "context": {
            "timestamp": "2025-01-20T10:00:00Z"
        }
    }
}

test_authorization_alcada_success if {
    result.allow == true with input as {
        "action": "desconto:aplicar",
        "user": {
            "id": "gerente@test.com",
            "tenant_id": "tenant-abc",
            "role": "GERENTE"
        },
        "resource": {
            "id": "locacao-123",
            "tenant_id": "tenant-abc"
        },
        "operation": {
            "percentual_desconto": 15 # Within GERENTE limit (25%)
        },
        "context": {
            "timestamp": "2025-01-20T10:00:00Z"
        }
    }
}

# ==================== Business Rules Integration ====================

test_authorization_business_rule_deny if {
    result.allow == false
    count(result.deny_reasons) > 0
    with input as {
        "action": "reserva:criar",
        "user": {
            "id": "vendedor@test.com",
            "tenant_id": "tenant-abc",
            "role": "VENDEDOR"
        },
        "resource": {
            "id": "reserva-new",
            "tenant_id": "tenant-abc",
            "jetski_id": "jetski-123"
        },
        "context": {
            "timestamp": "2025-01-20T10:00:00Z"
        }
    } with data.jetskis as {
        "jetski-123": {"status": "manutencao"} # Cannot reserve jetski in maintenance
    }
}

test_authorization_business_rule_success if {
    result.allow == true with input as {
        "action": "reserva:criar",
        "user": {
            "id": "vendedor@test.com",
            "tenant_id": "tenant-abc",
            "role": "VENDEDOR"
        },
        "resource": {
            "id": "reserva-new",
            "tenant_id": "tenant-abc",
            "jetski_id": "jetski-123"
        },
        "context": {
            "timestamp": "2025-01-20T10:00:00Z"
        }
    } with data.jetskis as {
        "jetski-123": {"status": "disponivel"}
    }
}

# ==================== Platform Admin Bypass ====================

test_platform_admin_bypasses_all_rules if {
    result.allow == true with input as {
        "action": "any:action",
        "user": {
            "id": "platform@system.com",
            "tenant_id": "platform",
            "role": "PLATFORM_ADMIN",
            "unrestricted_access": true
        },
        "resource": {
            "id": "resource-123",
            "tenant_id": "tenant-abc" # Different tenant
        },
        "context": {
            "timestamp": "2025-01-20T02:00:00Z" # Outside business hours
        }
    }
}

# ==================== ADMIN_TENANT Wildcard ====================

test_admin_tenant_has_wildcard_rbac if {
    result.allow == true with input as {
        "action": "any:custom:action",
        "user": {
            "id": "admin@test.com",
            "tenant_id": "tenant-abc",
            "role": "ADMIN_TENANT"
        },
        "resource": {
            "id": "resource-123",
            "tenant_id": "tenant-abc"
        },
        "context": {
            "timestamp": "2025-01-20T10:00:00Z"
        }
    }
}

test_admin_tenant_respects_tenant_isolation if {
    result.allow == false
    result.tenant_is_valid == false
    with input as {
        "action": "locacao:view",
        "user": {
            "id": "admin@test.com",
            "tenant_id": "tenant-abc",
            "role": "ADMIN_TENANT" # Has RBAC wildcard
        },
        "resource": {
            "id": "locacao-123",
            "tenant_id": "tenant-xyz" # But different tenant
        },
        "context": {
            "timestamp": "2025-01-20T10:00:00Z"
        }
    }
}

# ==================== Deny Reasons Aggregation ====================

test_deny_reasons_includes_all_violations if {
    result.allow == false

    # Should include RBAC deny
    "RBAC: role 'OPERADOR' não tem permissão para 'fechamento:mensal'" in result.deny_reasons

    with input as {
        "action": "fechamento:mensal",
        "user": {
            "id": "operador@test.com",
            "tenant_id": "tenant-abc",
            "role": "OPERADOR"
        },
        "resource": {
            "id": "fechamento-123",
            "tenant_id": "tenant-abc"
        },
        "context": {
            "timestamp": "2025-01-20T10:00:00Z"
        }
    }
}

test_deny_reasons_multi_tenant_violation if {
    result.allow == false
    "Multi-tenant: usuário e recurso pertencem a tenants diferentes" in result.deny_reasons
    with input as {
        "action": "locacao:view",
        "user": {
            "id": "operador@test.com",
            "tenant_id": "tenant-abc",
            "role": "OPERADOR"
        },
        "resource": {
            "id": "locacao-123",
            "tenant_id": "tenant-xyz"
        },
        "context": {
            "timestamp": "2025-01-20T10:00:00Z"
        }
    }
}

# ==================== Warnings Collection ====================

test_warnings_includes_business_warnings if {
    count(result.warnings) > 0 with input as {
        "action": "locacao:checkin",
        "user": {
            "id": "operador@test.com",
            "tenant_id": "tenant-abc",
            "role": "OPERADOR"
        },
        "resource": {
            "id": "locacao-123",
            "tenant_id": "tenant-abc",
            "jetski_id": "jetski-123"
        },
        "context": {
            "timestamp": "2025-01-20T10:00:00Z"
        }
    } with data.jetskis as {
        "jetski-123": {
            "status": "disponivel",
            "nivel_combustivel": 15 # Low fuel warning
        }
    }
}

test_warnings_includes_context_warnings if {
    count(result.warnings) > 0 with input as {
        "action": "fechamento:mensal",
        "user": {
            "id": "gerente@test.com",
            "tenant_id": "tenant-abc",
            "role": "GERENTE"
        },
        "resource": {
            "id": "fechamento-123",
            "tenant_id": "tenant-abc"
        },
        "context": {
            "timestamp": "2025-01-20T10:00:00Z",
            "user_agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X)" # Mobile warning
        }
    }
}

# ==================== Result Structure ====================

test_result_structure_complete if {
    # Validate that result contains all expected fields
    result.allow != null
    result.tenant_is_valid != null
    result.requer_aprovacao != null
    # aprovador_requerido may be null
    is_array(result.deny_reasons)
    is_array(result.warnings)
    with input as {
        "action": "locacao:view",
        "user": {
            "id": "operador@test.com",
            "tenant_id": "tenant-abc",
            "role": "OPERADOR"
        },
        "resource": {
            "id": "locacao-123",
            "tenant_id": "tenant-abc"
        },
        "context": {
            "timestamp": "2025-01-20T10:00:00Z"
        }
    }
}

# ==================== Complex Real-World Scenarios ====================

test_scenario_operador_checkin_success if {
    result.allow == true with input as {
        "action": "locacao:checkin",
        "user": {
            "id": "operador@pier.com",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001",
            "role": "OPERADOR"
        },
        "resource": {
            "id": "locacao-12345",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001",
            "jetski_id": "jetski-789"
        },
        "context": {
            "timestamp": "2025-01-20T14:30:00Z",
            "ip": "192.168.1.50",
            "device": "mobile"
        }
    } with data.jetskis as {
        "jetski-789": {"status": "disponivel"}
    }

    result.tenant_is_valid == true with input as {
        "action": "locacao:checkin",
        "user": {
            "id": "operador@pier.com",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001",
            "role": "OPERADOR"
        },
        "resource": {
            "id": "locacao-12345",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001",
            "jetski_id": "jetski-789"
        },
        "context": {
            "timestamp": "2025-01-20T14:30:00Z",
            "ip": "192.168.1.50",
            "device": "mobile"
        }
    } with data.jetskis as {
        "jetski-789": {"status": "disponivel"}
    }

    result.requer_aprovacao == false with input as {
        "action": "locacao:checkin",
        "user": {
            "id": "operador@pier.com",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001",
            "role": "OPERADOR"
        },
        "resource": {
            "id": "locacao-12345",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001",
            "jetski_id": "jetski-789"
        },
        "context": {
            "timestamp": "2025-01-20T14:30:00Z",
            "ip": "192.168.1.50",
            "device": "mobile"
        }
    } with data.jetskis as {
        "jetski-789": {"status": "disponivel"}
    }
}

test_scenario_gerente_apply_discount if {
    result.allow == true
    result.requer_aprovacao == false
    with input as {
        "action": "desconto:aplicar",
        "user": {
            "id": "gerente@office.com",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001",
            "role": "GERENTE"
        },
        "resource": {
            "id": "locacao-12345",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001"
        },
        "operation": {
            "percentual_desconto": 20 # Within GERENTE limit (25%)
        },
        "context": {
            "timestamp": "2025-01-20T16:00:00Z"
        }
    }
}

test_scenario_operador_large_discount_requires_approval if {
    result.allow == false
    result.requer_aprovacao == true
    result.aprovador_requerido == "GERENTE"
    with input as {
        "action": "desconto:aplicar",
        "user": {
            "id": "operador@pier.com",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001",
            "role": "OPERADOR"
        },
        "resource": {
            "id": "locacao-12345",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001"
        },
        "operation": {
            "percentual_desconto": 12 # Exceeds OPERADOR limit (10%)
        },
        "context": {
            "timestamp": "2025-01-20T15:00:00Z"
        }
    }
}

test_scenario_financeiro_monthly_closure if {
    result.allow == true with input as {
        "action": "fechamento:mensal",
        "user": {
            "id": "financeiro@office.com",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001",
            "role": "FINANCEIRO"
        },
        "resource": {
            "id": "fechamento-202501",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001"
        },
        "context": {
            "timestamp": "2025-02-01T10:00:00Z" # First day of month
        }
    }
}

test_scenario_mecanico_create_os if {
    result.allow == true with input as {
        "action": "os:criar",
        "user": {
            "id": "mecanico@workshop.com",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001",
            "role": "MECANICO"
        },
        "resource": {
            "id": "os-new",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001",
            "jetski_id": "jetski-789"
        },
        "context": {
            "timestamp": "2025-01-20T11:00:00Z"
        }
    }
}

# ==================== Edge Cases ====================

test_missing_input_fields if {
    # Should handle gracefully with minimal input
    result.allow == false with input as {
        "action": "unknown:action"
    }
}

test_empty_input if {
    # Should deny with empty input
    result.allow == false with input as {}
}

test_null_user if {
    result.allow == false with input as {
        "action": "locacao:view",
        "user": null
    }
}

test_null_resource if {
    result.allow == false with input as {
        "action": "locacao:view",
        "user": {"role": "OPERADOR", "tenant_id": "tenant-abc"}
    }
}
