package jetski.alcada

# Testes para política de Alçada (Approval Authority)
# Executar com: opa test -v src/main/resources/opa/policies/

# ==================== Desconto: OPERADOR Tests ====================

test_operador_can_apply_5_percent_desconto if {
    allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 5}
    }
}

test_operador_can_apply_10_percent_desconto if {
    allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 10}
    }
}

test_operador_cannot_apply_11_percent_desconto if {
    not allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 11}
    }
}

test_operador_11_percent_requires_gerente if {
    requer_aprovacao with input as {
        "user": {"role": "OPERADOR"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 11}
    }

    aprovador_requerido == "GERENTE" with input as {
        "user": {"role": "OPERADOR"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 11}
    }
}

test_operador_26_percent_requires_admin if {
    requer_aprovacao with input as {
        "user": {"role": "OPERADOR"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 26}
    }

    aprovador_requerido == "ADMIN_TENANT" with input as {
        "user": {"role": "OPERADOR"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 26}
    }
}

# ==================== Desconto: GERENTE Tests ====================

test_gerente_can_apply_15_percent_desconto if {
    allow with input as {
        "user": {"role": "GERENTE"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 15}
    }
}

test_gerente_can_apply_25_percent_desconto if {
    allow with input as {
        "user": {"role": "GERENTE"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 25}
    }
}

test_gerente_cannot_apply_26_percent_desconto if {
    not allow with input as {
        "user": {"role": "GERENTE"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 26}
    }
}

test_gerente_26_percent_requires_admin if {
    requer_aprovacao with input as {
        "user": {"role": "GERENTE"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 26}
    }

    aprovador_requerido == "ADMIN_TENANT" with input as {
        "user": {"role": "GERENTE"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 26}
    }
}

# ==================== Desconto: ADMIN_TENANT Tests ====================

test_admin_can_apply_30_percent_desconto if {
    allow with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 30}
    }
}

test_admin_can_apply_50_percent_desconto if {
    allow with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 50}
    }
}

test_admin_cannot_apply_51_percent_desconto if {
    not allow with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 51}
    }
}

test_admin_51_percent_requires_platform_approval if {
    requer_aprovacao with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 51}
    }

    aprovador_requerido == "PLATFORM_ADMIN" with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 51}
    }
}

# ==================== OS Approval: OPERADOR Tests ====================

test_operador_can_approve_1000_os if {
    allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "os:aprovar",
        "operation": {"valor_os": 1000}
    }
}

test_operador_can_approve_2000_os if {
    allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "os:aprovar",
        "operation": {"valor_os": 2000}
    }
}

test_operador_cannot_approve_2001_os if {
    not allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "os:aprovar",
        "operation": {"valor_os": 2001}
    }
}

test_operador_2001_os_requires_gerente if {
    requer_aprovacao with input as {
        "user": {"role": "OPERADOR"},
        "action": "os:aprovar",
        "operation": {"valor_os": 2001}
    }

    aprovador_requerido == "GERENTE" with input as {
        "user": {"role": "OPERADOR"},
        "action": "os:aprovar",
        "operation": {"valor_os": 2001}
    }
}

# ==================== OS Approval: GERENTE Tests ====================

test_gerente_can_approve_5000_os if {
    allow with input as {
        "user": {"role": "GERENTE"},
        "action": "os:aprovar",
        "operation": {"valor_os": 5000}
    }
}

test_gerente_can_approve_10000_os if {
    allow with input as {
        "user": {"role": "GERENTE"},
        "action": "os:aprovar",
        "operation": {"valor_os": 10000}
    }
}

test_gerente_cannot_approve_10001_os if {
    not allow with input as {
        "user": {"role": "GERENTE"},
        "action": "os:aprovar",
        "operation": {"valor_os": 10001}
    }
}

test_gerente_10001_os_requires_admin if {
    requer_aprovacao with input as {
        "user": {"role": "GERENTE"},
        "action": "os:aprovar",
        "operation": {"valor_os": 10001}
    }

    aprovador_requerido == "ADMIN_TENANT" with input as {
        "user": {"role": "GERENTE"},
        "action": "os:aprovar",
        "operation": {"valor_os": 10001}
    }
}

# ==================== OS Approval: ADMIN_TENANT Tests ====================

test_admin_can_approve_50000_os if {
    allow with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "os:aprovar",
        "operation": {"valor_os": 50000}
    }
}

test_admin_can_approve_100000_os if {
    allow with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "os:aprovar",
        "operation": {"valor_os": 100000}
    }
}

# ==================== Edge Cases ====================

test_desconto_without_percentual_denied if {
    not allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "desconto:aplicar",
        "operation": {}
    }
}

test_os_without_valor_denied if {
    not allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "os:aprovar",
        "operation": {}
    }
}

test_negative_desconto_denied if {
    not allow with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": -5}
    }
}

test_negative_valor_os_denied if {
    not allow with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "os:aprovar",
        "operation": {"valor_os": -100}
    }
}

test_zero_desconto_allowed if {
    allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 0}
    }
}

test_zero_valor_os_allowed if {
    allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "os:aprovar",
        "operation": {"valor_os": 0}
    }
}

# ==================== Non-alcada Actions ====================

test_non_alcada_action_passes_through if {
    # Actions that don't have alçada rules should pass through
    # (will be handled by RBAC)
    allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "locacao:checkin"
    }
}

test_alcada_does_not_block_rbac_permissions if {
    # GERENTE should be able to do non-alçada actions via RBAC
    allow with input as {
        "user": {"role": "GERENTE"},
        "action": "locacao:list"
    }
}

# ==================== Boundary Tests ====================

test_operador_boundary_10_percent if {
    allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 10.0}
    }
}

test_operador_boundary_10_01_percent if {
    not allow with input as {
        "user": {"role": "OPERADOR"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 10.01}
    }
}

test_gerente_boundary_25_percent if {
    allow with input as {
        "user": {"role": "GERENTE"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 25.0}
    }
}

test_gerente_boundary_25_01_percent if {
    not allow with input as {
        "user": {"role": "GERENTE"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 25.01}
    }
}

test_admin_boundary_50_percent if {
    allow with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 50.0}
    }
}

test_admin_boundary_50_01_percent if {
    not allow with input as {
        "user": {"role": "ADMIN_TENANT"},
        "action": "desconto:aplicar",
        "operation": {"percentual_desconto": 50.01}
    }
}
