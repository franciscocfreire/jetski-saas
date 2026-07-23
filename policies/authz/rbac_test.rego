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


# ==================== FINANCEIRO Tests ====================


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

# ==================== Pagamento / Balcão (Fase 1) ====================

test_financeiro_can_confirmar_sinal if {
    allow_rbac with input as {"user": {"role": "FINANCEIRO"}, "action": "reserva:confirmar-sinal"}
}

test_financeiro_can_recusar_pagamento if {
    allow_rbac with input as {"user": {"role": "FINANCEIRO"}, "action": "reserva:recusar-pagamento"}
}

test_financeiro_can_list_reservas if {
    allow_rbac with input as {"user": {"role": "FINANCEIRO"}, "action": "reserva:list"}
}

test_operador_can_confirmar_sinal if {
    allow_rbac with input as {"user": {"role": "OPERADOR"}, "action": "reserva:confirmar-sinal"}
}

test_operador_can_recusar_pagamento if {
    allow_rbac with input as {"user": {"role": "OPERADOR"}, "action": "reserva:recusar-pagamento"}
}

test_operador_can_create_pre_conta if {
    allow_rbac with input as {"user": {"role": "OPERADOR"}, "action": "cliente:create"}
}

test_operador_can_emitir_documentos if {
    allow_rbac with input as {"user": {"role": "OPERADOR"}, "action": "reserva:emitir-documentos"}
}

# Config de compressão de imagem: lida no upload por quem opera o balcão.
test_operador_can_read_imagem_config if {
    allow_rbac with input as {"user": {"role": "OPERADOR"}, "action": "documento:imagem-config"}
}

test_gerente_can_read_imagem_config if {
    allow_rbac with input as {"user": {"role": "GERENTE"}, "action": "documento:imagem-config"}
}

test_operador_can_registrar_habilitacao if {
    allow_rbac with input as {"user": {"role": "OPERADOR"}, "action": "reserva:habilitacao"}
}

test_gerente_can_registrar_habilitacao if {
    allow_rbac with input as {"user": {"role": "GERENTE"}, "action": "reserva:habilitacao"}
}

test_operador_can_registrar_aceite if {
    allow_rbac with input as {"user": {"role": "OPERADOR"}, "action": "reserva:aceite"}
}

# Negativos
test_mecanico_cannot_confirmar_sinal if {
    not allow_rbac with input as {"user": {"role": "MECANICO"}, "action": "reserva:confirmar-sinal"}
}

test_mecanico_cannot_emitir_documentos if {
    not allow_rbac with input as {"user": {"role": "MECANICO"}, "action": "reserva:emitir-documentos"}
}

# =============================================================================
# user_permissions (permissões efetivas para o menu/tela de permissões)
# =============================================================================

test_user_permissions_operador_contains_locacao_list if {
    "locacao:list" in user_permissions with input as {"user": {"roles": ["OPERADOR"]}}
}

test_user_permissions_operador_not_contains_os_create if {
    not "os:create" in user_permissions with input as {"user": {"roles": ["OPERADOR"]}}
}

test_user_permissions_multi_role_is_union if {
    perms := user_permissions with input as {"user": {"roles": ["OPERADOR", "MECANICO"]}}
    "locacao:checkin" in perms  # de OPERADOR
    "os:create" in perms        # de MECANICO
}

test_user_permissions_admin_is_wildcard if {
    user_permissions == {"*"} with input as {"user": {"roles": ["ADMIN_TENANT"]}}
}

test_user_permissions_empty_roles_is_empty if {
    count(user_permissions) == 0 with input as {"user": {"roles": []}}
}

test_user_permissions_unknown_role_is_empty if {
    count(user_permissions) == 0 with input as {"user": {"roles": ["PAPEL_INEXISTENTE"]}}
}
