package jetski.rbac

# Testes para política RBAC
# Executar com: opa test -v src/test/resources/opa/policies/

# ==================== Helper Functions ====================

# Alias para action_matches_permission (para testes de wildcard)
matches_pattern(action, pattern) := action_matches_permission(action, pattern)

# ==================== OPERADOR Tests ====================

test_operador_can_list_locacoes if {
    allow_rbac with input as {
        "user": {"role": "OPERADOR"},
        "action": "locacao:list"
    }
}

test_operador_can_checkin if {
    allow_rbac with input as {
        "user": {"role": "OPERADOR"},
        "action": "locacao:checkin"
    }
}

test_operador_can_checkout if {
    allow_rbac with input as {
        "user": {"role": "OPERADOR"},
        "action": "locacao:checkout"
    }
}

test_operador_can_register_abastecimento if {
    allow_rbac with input as {
        "user": {"role": "OPERADOR"},
        "action": "abastecimento:registrar"
    }
}

test_operador_cannot_apply_desconto if {
    not allow_rbac with input as {
        "user": {"role": "OPERADOR"},
        "action": "desconto:aplicar"
    }
}

test_operador_cannot_close_month if {
    not allow_rbac with input as {
        "user": {"role": "OPERADOR"},
        "action": "fechamento:mensal"
    }
}

test_operador_cannot_approve_os if {
    not allow_rbac with input as {
        "user": {"role": "OPERADOR"},
        "action": "os:aprovar"
    }
}

# ==================== GERENTE Tests ====================

test_gerente_has_wildcard_locacao if {
    allow_rbac with input as {
        "user": {"role": "GERENTE"},
        "action": "locacao:list"
    }

    allow_rbac with input as {
        "user": {"role": "GERENTE"},
        "action": "locacao:create"
    }

    allow_rbac with input as {
        "user": {"role": "GERENTE"},
        "action": "locacao:checkin"
    }

    allow_rbac with input as {
        "user": {"role": "GERENTE"},
        "action": "locacao:checkout"
    }
}

test_gerente_can_apply_desconto if {
    allow_rbac with input as {
        "user": {"role": "GERENTE"},
        "action": "desconto:aplicar"
    }
}

test_gerente_can_approve_os if {
    allow_rbac with input as {
        "user": {"role": "GERENTE"},
        "action": "os:aprovar"
    }
}

test_gerente_can_close_day if {
    allow_rbac with input as {
        "user": {"role": "GERENTE"},
        "action": "fechamento:diario"
    }
}

test_gerente_cannot_close_month if {
    not allow_rbac with input as {
        "user": {"role": "GERENTE"},
        "action": "fechamento:mensal"
    }
}

# ==================== FINANCEIRO Tests ====================

test_financeiro_can_close_day if {
    allow_rbac with input as {
        "user": {"role": "FINANCEIRO"},
        "action": "fechamento:diario"
    }
}

test_financeiro_can_close_month if {
    allow_rbac with input as {
        "user": {"role": "FINANCEIRO"},
        "action": "fechamento:mensal"
    }
}

test_financeiro_can_list_comissoes if {
    allow_rbac with input as {
        "user": {"role": "FINANCEIRO"},
        "action": "comissao:list"
    }
}

test_financeiro_cannot_checkin if {
    not allow_rbac with input as {
        "user": {"role": "FINANCEIRO"},
        "action": "locacao:checkin"
    }
}

# ==================== MECANICO Tests ====================

test_mecanico_can_create_os if {
    allow_rbac with input as {
        "user": {"role": "MECANICO"},
        "action": "os:criar"
    }
}

test_mecanico_can_close_os if {
    allow_rbac with input as {
        "user": {"role": "MECANICO"},
        "action": "os:fechar"
    }
}

test_mecanico_can_update_os if {
    allow_rbac with input as {
        "user": {"role": "MECANICO"},
        "action": "os:update"
    }
}

test_mecanico_cannot_approve_os if {
    not allow_rbac with input as {
        "user": {"role": "MECANICO"},
        "action": "os:aprovar"
    }
}

test_mecanico_cannot_checkin if {
    not allow_rbac with input as {
        "user": {"role": "MECANICO"},
        "action": "locacao:checkin"
    }
}

# ==================== VENDEDOR Tests ====================

test_vendedor_can_create_reserva if {
    allow_rbac with input as {
        "user": {"role": "VENDEDOR"},
        "action": "reserva:criar"
    }
}

test_vendedor_can_list_clientes if {
    allow_rbac with input as {
        "user": {"role": "VENDEDOR"},
        "action": "cliente:list"
    }
}

test_vendedor_can_view_own_comissao if {
    allow_rbac with input as {
        "user": {"role": "VENDEDOR"},
        "action": "comissao:view:own"
    }
}

test_vendedor_cannot_checkin if {
    not allow_rbac with input as {
        "user": {"role": "VENDEDOR"},
        "action": "locacao:checkin"
    }
}

test_vendedor_cannot_close_day if {
    not allow_rbac with input as {
        "user": {"role": "VENDEDOR"},
        "action": "fechamento:diario"
    }
}

# ==================== ADMIN_TENANT Tests ====================

test_admin_tenant_has_wildcard_access if {
    allow_rbac with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "locacao:checkin"
    }

    allow_rbac with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "fechamento:mensal"
    }

    allow_rbac with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "os:aprovar"
    }

    allow_rbac with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "any:random:action"
    }
}

# ==================== Platform Admin Tests ====================

test_platform_admin_unrestricted if {
    allow_platform with input as {
        "user": {
            "role": "PLATFORM_ADMIN",
            "unrestricted_access": true
        },
        "action": "tenant:create"
    }
}

# ==================== Wildcard Pattern Matching ====================

test_wildcard_locacao_matches_all if {
    count([action |
        some action in ["locacao:list", "locacao:create", "locacao:checkin", "locacao:checkout", "locacao:desconto"]
        matches_pattern(action, "locacao:*")
    ]) == 5
}

test_wildcard_exact_match if {
    matches_pattern("locacao:list", "locacao:list")
}

test_wildcard_no_match if {
    not matches_pattern("reserva:create", "locacao:*")
}

test_star_wildcard_matches_all if {
    matches_pattern("any:action", "*")
}

# ==================== Unknown Role ====================

test_unknown_role_denied if {
    not allow_rbac with input as {
        "user": {"role": "UNKNOWN_ROLE"},
        "action": "locacao:list"
    }
}

test_missing_role_denied if {
    not allow_rbac with input as {
        "user": {},
        "action": "locacao:list"
    }
}

# ==================== Edge Cases ====================

test_empty_action_denied if {
    not allow_rbac with input as {
        "user": {"role": "OPERADOR"},
        "action": ""
    }
}

test_case_sensitive_role if {
    not allow_rbac with input as {
        "user": {"role": "operador"}, # lowercase
        "action": "locacao:list"
    }
}

test_case_sensitive_action if {
    not allow_rbac with input as {
        "user": {"role": "OPERADOR"},
        "action": "LOCACAO:LIST" # uppercase
    }
}
