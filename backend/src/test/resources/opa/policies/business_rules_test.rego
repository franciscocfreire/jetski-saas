package jetski.business

# Testes para Business Rules (regras de negócio específicas do domínio)
# Executar com: opa test -v src/test/resources/opa/policies/

# ==================== RN06: Jetski em Manutenção ====================

test_cannot_reserve_jetski_in_maintenance if {
    count(deny_manutencao) > 0 with input as {
        "action": "reserva:criar",
        "resource": {"jetski_id": "jetski-123"}
    } with data.jetskis as {
        "jetski-123": {"status": "manutencao"}
    }
}

test_can_reserve_jetski_available if {
    count(deny_manutencao) == 0 with input as {
        "action": "reserva:criar",
        "resource": {"jetski_id": "jetski-123"}
    } with data.jetskis as {
        "jetski-123": {"status": "disponivel"}
    }
}

test_can_reserve_jetski_in_use if {
    # "locado" is allowed for future reservations
    count(deny_manutencao) == 0 with input as {
        "action": "reserva:criar",
        "resource": {"jetski_id": "jetski-123"}
    } with data.jetskis as {
        "jetski-123": {"status": "locado"}
    }
}

test_manutencao_rule_not_applied_to_other_actions if {
    count(deny_manutencao) == 0 with input as {
        "action": "locacao:checkin",
        "resource": {"jetski_id": "jetski-123"}
    } with data.jetskis as {
        "jetski-123": {"status": "manutencao"}
    }
}

# ==================== Lifecycle: Checkout Requires Checkin ====================

test_cannot_checkout_without_checkin if {
    count(deny_lifecycle) > 0 with input as {
        "action": "locacao:checkout",
        "resource": {"id": "locacao-123"}
    } with data.locacoes as {
        "locacao-123": {"checkin_realizado": false, "checkout_realizado": false}
    }
}

test_can_checkout_after_checkin if {
    count(deny_lifecycle) == 0 with input as {
        "action": "locacao:checkout",
        "resource": {"id": "locacao-123"}
    } with data.locacoes as {
        "locacao-123": {"checkin_realizado": true, "checkout_realizado": false}
    }
}

test_cannot_checkout_already_finished if {
    count(deny_lifecycle) > 0 with input as {
        "action": "locacao:checkout",
        "resource": {"id": "locacao-123"}
    } with data.locacoes as {
        "locacao-123": {"checkin_realizado": true, "checkout_realizado": true}
    }
}

test_lifecycle_rule_not_applied_to_other_actions if {
    count(deny_lifecycle) == 0 with input as {
        "action": "locacao:view",
        "resource": {"id": "locacao-123"}
    } with data.locacoes as {
        "locacao-123": {"checkin_realizado": false, "checkout_realizado": false}
    }
}

# ==================== Schedule Conflicts ====================

test_cannot_create_overlapping_reservation if {
    count(deny_conflito) > 0 with input as {
        "action": "reserva:criar",
        "operation": {
            "reserva": {
                "jetski_id": "jetski-123",
                "hora_inicio": "2025-01-15T10:00:00Z",
                "hora_fim": "2025-01-15T12:00:00Z"
            }
        }
    } with data.reservas as {
        "reserva-existing": {
            "jetski_id": "jetski-123",
            "hora_inicio": "2025-01-15T11:00:00Z",
            "hora_fim": "2025-01-15T13:00:00Z",
            "status": "confirmada"
        }
    }
}

test_can_create_non_overlapping_reservation if {
    count(deny_conflito) == 0 with input as {
        "action": "reserva:criar",
        "operation": {
            "reserva": {
                "jetski_id": "jetski-123",
                "hora_inicio": "2025-01-15T14:00:00Z",
                "hora_fim": "2025-01-15T16:00:00Z"
            }
        }
    } with data.reservas as {
        "reserva-existing": {
            "jetski_id": "jetski-123",
            "hora_inicio": "2025-01-15T10:00:00Z",
            "hora_fim": "2025-01-15T12:00:00Z",
            "status": "confirmada"
        }
    }
}

test_can_create_reservation_different_jetski if {
    count(deny_conflito) == 0 with input as {
        "action": "reserva:criar",
        "operation": {
            "reserva": {
                "jetski_id": "jetski-456",
                "hora_inicio": "2025-01-15T11:00:00Z",
                "hora_fim": "2025-01-15T13:00:00Z"
            }
        }
    } with data.reservas as {
        "reserva-existing": {
            "jetski_id": "jetski-123",
            "hora_inicio": "2025-01-15T11:00:00Z",
            "hora_fim": "2025-01-15T13:00:00Z",
            "status": "confirmada"
        }
    }
}

test_cancelled_reservation_does_not_block if {
    count(deny_conflito) == 0 with input as {
        "action": "reserva:criar",
        "operation": {
            "reserva": {
                "jetski_id": "jetski-123",
                "hora_inicio": "2025-01-15T11:00:00Z",
                "hora_fim": "2025-01-15T13:00:00Z"
            }
        }
    } with data.reservas as {
        "reserva-existing": {
            "jetski_id": "jetski-123",
            "hora_inicio": "2025-01-15T11:00:00Z",
            "hora_fim": "2025-01-15T13:00:00Z",
            "status": "cancelada"
        }
    }
}

# ==================== Fuel Policy ====================

test_cannot_register_fuel_without_policy if {
    count(deny_combustivel) > 0 with input as {
        "action": "abastecimento:registrar",
        "user": {"tenant_id": "tenant-123"},
        "resource": {"jetski_id": "jetski-123"}
    } with data.tenants as {
        "tenant-123": {"fuel_policy": "incluso"}
    }
}

test_can_register_fuel_with_policy if {
    count(deny_combustivel) == 0 with input as {
        "action": "abastecimento:registrar",
        "user": {"tenant_id": "tenant-123"},
        "resource": {"jetski_id": "jetski-123"}
    } with data.tenants as {
        "tenant-123": {"fuel_policy": "medido"}
    }
}

test_can_register_fuel_with_taxa_fixa_policy if {
    count(deny_combustivel) == 0 with input as {
        "action": "abastecimento:registrar",
        "user": {"tenant_id": "tenant-123"},
        "resource": {"jetski_id": "jetski-123"}
    } with data.tenants as {
        "tenant-123": {"fuel_policy": "taxa_fixa"}
    }
}

test_fuel_rule_not_applied_to_other_actions if {
    count(deny_combustivel) == 0 with input as {
        "action": "locacao:checkin",
        "user": {"tenant_id": "tenant-123"},
        "resource": {"jetski_id": "jetski-123"}
    } with data.tenants as {
        "tenant-123": {"fuel_policy": "incluso"}
    }
}

# ==================== Mandatory Photos ====================

test_cannot_checkout_without_4_photos if {
    count(deny_fotos) > 0 with input as {
        "action": "locacao:checkout",
        "operation": {"fotos": ["foto1.jpg", "foto2.jpg", "foto3.jpg"]}
    }
}

test_can_checkout_with_4_photos if {
    count(deny_fotos) == 0 with input as {
        "action": "locacao:checkout",
        "operation": {"fotos": ["foto1.jpg", "foto2.jpg", "foto3.jpg", "foto4.jpg"]}
    }
}

test_can_checkout_with_more_than_4_photos if {
    count(deny_fotos) == 0 with input as {
        "action": "locacao:checkout",
        "operation": {"fotos": ["foto1.jpg", "foto2.jpg", "foto3.jpg", "foto4.jpg", "foto5.jpg", "foto6.jpg"]}
    }
}

test_photos_4_required_for_checkin if {
    count(deny_fotos) == 0 with input as {
        "action": "locacao:checkin",
        "operation": {"fotos": ["foto1.jpg", "foto2.jpg", "foto3.jpg", "foto4.jpg"]}
    }
}

test_photos_not_required_for_other_actions if {
    count(deny_fotos) == 0 with input as {
        "action": "locacao:view",
        "operation": {"fotos": []}
    }
}

# ==================== Daily/Monthly Closure Locking ====================

test_cannot_edit_locked_day if {
    count(deny_fechamento) > 0 with input as {
        "action": "locacao:update",
        "user": {"tenant_id": "tenant-123"},
        "resource": {"id": "locacao-123"}
    } with data.resources as {
        "locacao-123": {"data": "2025-01-10"}
    } with data.fechamentos_diarios as [
        {
            "tenant_id": "tenant-123",
            "data": "2025-01-10",
            "status": "fechado"
        }
    ]
}

test_can_edit_unlocked_day if {
    count(deny_fechamento) == 0 with input as {
        "action": "locacao:update",
        "user": {"tenant_id": "tenant-123"},
        "resource": {"id": "locacao-123"}
    } with data.resources as {
        "locacao-123": {"data": "2025-01-10"}
    } with data.fechamentos_diarios as [
        {
            "tenant_id": "tenant-123",
            "data": "2025-01-10",
            "status": "aberto"
        }
    ]
}

test_can_edit_day_without_closure if {
    count(deny_fechamento) == 0 with input as {
        "action": "locacao:update",
        "user": {"tenant_id": "tenant-123"},
        "resource": {"id": "locacao-123"}
    } with data.resources as {
        "locacao-123": {"data": "2025-01-15"}
    } with data.fechamentos_diarios as [
        {
            "tenant_id": "tenant-123",
            "data": "2025-01-10",
            "status": "fechado"
        }
    ]
}

test_closure_lock_applies_to_delete if {
    count(deny_fechamento) > 0 with input as {
        "action": "desconto:aplicar",
        "user": {"tenant_id": "tenant-123"},
        "resource": {"id": "locacao-123"}
    } with data.resources as {
        "locacao-123": {"data": "2025-01-10"}
    } with data.fechamentos_diarios as [
        {
            "tenant_id": "tenant-123",
            "data": "2025-01-10",
            "status": "fechado"
        }
    ]
}

test_closure_lock_not_applied_to_view if {
    count(deny_fechamento) == 0 with input as {
        "action": "locacao:view",
        "user": {"tenant_id": "tenant-123"},
        "resource": {"id": "locacao-123"}
    } with data.resources as {
        "locacao-123": {"data": "2025-01-10"}
    } with data.fechamentos_diarios as [
        {
            "tenant_id": "tenant-123",
            "data": "2025-01-10",
            "status": "fechado"
        }
    ]
}

# ==================== Combined Business Rules ====================

test_all_business_rules_pass if {
    allow_business with input as {
        "action": "locacao:checkout",
        "resource": {
            "id": "locacao-123",
            "jetski_id": "jetski-123",
            "dt_checkin": "2025-01-15T10:00:00Z"
        },
        "operation": {"fotos": ["foto1.jpg", "foto2.jpg", "foto3.jpg", "foto4.jpg"]}
    } with data.locacoes as {
        "locacao-123": {"checkin_realizado": true, "checkout_realizado": false}
    } with data.jetskis as {
        "jetski-123": {"status": "locado"}
    } with data.fechamentos_diarios as {}
}

test_any_deny_blocks_request if {
    not allow_business with input as {
        "action": "locacao:update",
        "user": {"tenant_id": "tenant-123"},
        "resource": {
            "id": "locacao-123"
        },
        "operation": {"fotos": ["foto1.jpg", "foto2.jpg", "foto3.jpg", "foto4.jpg"]}
    } with data.resources as {
        "locacao-123": {"data": "2025-01-10"}
    } with data.locacoes as {
        "locacao-123": {"checkin_realizado": true, "checkout_realizado": false}
    } with data.jetskis as {
        "jetski-123": {"status": "locado"}
    } with data.fechamentos_diarios as [
        {
            "tenant_id": "tenant-123",
            "data": "2025-01-10",
            "status": "fechado"
        }
    ]
}

# ==================== Edge Cases ====================

test_missing_jetski_data_allows if {
    # If jetski data not available, rule doesn't deny (fail-open for data availability)
    count(deny_manutencao) == 0 with input as {
        "action": "reserva:criar",
        "resource": {"jetski_id": "jetski-nonexistent"}
    } with data.jetskis as {}
}

test_missing_locacao_data_allows if {
    count(deny_lifecycle) == 0 with input as {
        "action": "locacao:checkout",
        "resource": {"id": "locacao-nonexistent"}
    } with data.locacoes as {}
}

test_empty_reservas_allows if {
    count(deny_conflito) == 0 with input as {
        "action": "reserva:criar",
        "operation": {
            "reserva": {
                "jetski_id": "jetski-123",
                "hora_inicio": "2025-01-15T10:00:00Z",
                "hora_fim": "2025-01-15T12:00:00Z"
            }
        }
    } with data.reservas as {}
}

# ==================== Warnings ====================
# NOTE: Warning rules not yet implemented in business_rules.rego
# Uncomment when warnings are added to the policy

# test_warning_low_fuel_level if {
#     count(warnings) > 0 with input as {
#         "action": "locacao:checkin",
#         "resource": {"jetski_id": "jetski-123"}
#     } with data.jetskis as {
#         "jetski-123": {"nivel_combustivel": 15}
#     }
# }

# test_no_warning_normal_fuel_level if {
#     count(warnings) == 0 with input as {
#         "action": "locacao:checkin",
#         "resource": {"jetski_id": "jetski-123"}
#     } with data.jetskis as {
#         "jetski-123": {"nivel_combustivel": 60}
#     }
# }

# test_warning_jetski_near_maintenance if {
#     count(warnings) > 0 with input as {
#         "action": "locacao:checkin",
#         "resource": {"jetski_id": "jetski-123"}
#     } with data.jetskis as {
#         "jetski-123": {
#             "horimetro": 95,
#             "proxima_manutencao": 100
#         }
#     }
# }

# test_no_warning_jetski_far_from_maintenance if {
#     count(warnings) == 0 with input as {
#         "action": "locacao:checkin",
#         "resource": {"jetski_id": "jetski-123"}
#     } with data.jetskis as {
#         "jetski-123": {
#             "horimetro": 50,
#             "proxima_manutencao": 100
#         }
#     }
# }
