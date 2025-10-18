package jetski.rbac

# Testes para política RBAC
# Executar com: opa test -v src/main/resources/opa/policies/

# ==================== OPERADOR Tests ====================

test_operador_can_list_locacoes if {
    allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "locacao:list"
    }
}

test_operador_can_checkin if {
    allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "locacao:checkin"
    }
}

test_operador_can_checkout if {
    allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "locacao:checkout"
    }
}

test_operador_can_register_abastecimento if {
    allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "abastecimento:registrar"
    }
}

test_operador_cannot_apply_desconto if {
    not allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "desconto:aplicar"
    }
}

test_operador_cannot_close_month if {
    not allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "fechamento:mensal"
    }
}

test_operador_cannot_approve_os if {
    not allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "os:aprovar"
    }
}

# ==================== GERENTE Tests ====================

test_gerente_has_wildcard_locacao if {
    allow with input as {
        "user": {"role": "GERENTE"},
        "action": "locacao:list"
    }

    allow with input as {
        "user": {"role": "GERENTE"},
        "action": "locacao:create"
    }

    allow with input as {
        "user": {"role": "GERENTE"},
        "action": "locacao:checkin"
    }

    allow with input as {
        "user": {"role": "GERENTE"},
        "action": "locacao:checkout"
    }
}

test_gerente_can_apply_desconto if {
    allow with input as {
        "user": {"role": "GERENTE"},
        "action": "desconto:aplicar"
    }
}

test_gerente_can_approve_os if {
    allow with input as {
        "user": {"role": "GERENTE"},
        "action": "os:aprovar"
    }
}

test_gerente_can_close_day if {
    allow with input as {
        "user": {"role": "GERENTE"},
        "action": "fechamento:diario"
    }
}

test_gerente_cannot_close_month if {
    not allow with input as {
        "user": {"role": "GERENTE"},
        "action": "fechamento:mensal"
    }
}

# ==================== FINANCEIRO Tests ====================

test_financeiro_can_close_day if {
    allow with input as {
        "user": {"role": "FINANCEIRO"},
        "action": "fechamento:diario"
    }
}

test_financeiro_can_close_month if {
    allow with input as {
        "user": {"role": "FINANCEIRO"},
        "action": "fechamento:mensal"
    }
}

test_financeiro_can_list_comissoes if {
    allow with input as {
        "user": {"role": "FINANCEIRO"},
        "action": "comissao:list"
    }
}

test_financeiro_cannot_checkin if {
    not allow with input as {
        "user": {"role": "FINANCEIRO"},
        "action": "locacao:checkin"
    }
}

# ==================== MECANICO Tests ====================

test_mecanico_can_create_os if {
    allow with input as {
        "user": {"role": "MECANICO"},
        "action": "os:create"
    }
}

test_mecanico_can_close_os if {
    allow with input as {
        "user": {"role": "MECANICO"},
        "action": "os:fechar"
    }
}

test_mecanico_can_manage_jetski if {
    allow with input as {
        "user": {"role": "MECANICO"},
        "action": "jetski:update"
    }
}

test_mecanico_cannot_approve_os if {
    not allow with input as {
        "user": {"role": "MECANICO"},
        "action": "os:aprovar"
    }
}

test_mecanico_cannot_checkin if {
    not allow with input as {
        "user": {"role": "MECANICO"},
        "action": "locacao:checkin"
    }
}

# ==================== VENDEDOR Tests ====================

test_vendedor_can_create_reserva if {
    allow with input as {
        "user": {"role": "VENDEDOR"},
        "action": "reserva:create"
    }
}

test_vendedor_can_list_clientes if {
    allow with input as {
        "user": {"role": "VENDEDOR"},
        "action": "cliente:list"
    }
}

test_vendedor_can_view_comissao if {
    allow with input as {
        "user": {"role": "VENDEDOR"},
        "action": "comissao:view"
    }
}

test_vendedor_cannot_checkin if {
    not allow with input as {
        "user": {"role": "VENDEDOR"},
        "action": "locacao:checkin"
    }
}

test_vendedor_cannot_close_day if {
    not allow with input as {
        "user": {"role": "VENDEDOR"},
        "action": "fechamento:diario"
    }
}

# ==================== ADMIN_TENANT Tests ====================

test_admin_tenant_has_wildcard_access if {
    allow with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "locacao:checkin"
    }

    allow with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "fechamento:mensal"
    }

    allow with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "os:aprovar"
    }

    allow with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "any:random:action"
    }
}

# ==================== Platform Admin Tests ====================

test_platform_admin_unrestricted if {
    allow with input as {
        "user": {
            "role": "PLATFORM_ADMIN",
            "unrestricted_access": true
        },
        "action": "any:action"
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
    not allow with input as {
        "user": {"role": "UNKNOWN_ROLE"},
        "action": "locacao:list"
    }
}

test_missing_role_denied if {
    not allow with input as {
        "user": {},
        "action": "locacao:list"
    }
}

# ==================== Edge Cases ====================

test_empty_action_denied if {
    not allow with input as {
        "user": {"role": "OPERADOR"},
        "action": ""
    }
}

test_case_sensitive_role if {
    not allow with input as {
        "user": {"role": "operador"}, # lowercase
        "action": "locacao:list"
    }
}

test_case_sensitive_action if {
    not allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "LOCACAO:LIST" # uppercase
    }
}
