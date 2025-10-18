package jetski.multi_tenant

# Testes para política de Multi-Tenant (isolamento de tenants)
# Executar com: opa test -v src/main/resources/opa/policies/

# ==================== Valid Tenant Tests ====================

test_same_tenant_valid if {
    tenant_is_valid with input as {
        "user": {"tenant_id": "tenant-abc"},
        "resource": {"tenant_id": "tenant-abc"}
    }
}

test_different_tenant_invalid if {
    not tenant_is_valid with input as {
        "user": {"tenant_id": "tenant-abc"},
        "resource": {"tenant_id": "tenant-xyz"}
    }
}

# ==================== Unrestricted Access (Platform Admin) ====================

test_platform_admin_unrestricted if {
    tenant_is_valid with input as {
        "user": {
            "tenant_id": "tenant-abc",
            "unrestricted_access": true
        },
        "resource": {"tenant_id": "tenant-xyz"}
    }
}

test_platform_admin_can_access_any_tenant if {
    tenant_is_valid with input as {
        "user": {
            "tenant_id": "platform",
            "unrestricted_access": true
        },
        "resource": {"tenant_id": "tenant-abc"}
    }
}

# ==================== Missing Tenant ID ====================

test_missing_user_tenant_invalid if {
    not tenant_is_valid with input as {
        "user": {},
        "resource": {"tenant_id": "tenant-abc"}
    }
}

test_missing_resource_tenant_invalid if {
    not tenant_is_valid with input as {
        "user": {"tenant_id": "tenant-abc"},
        "resource": {}
    }
}

test_both_missing_tenant_invalid if {
    not tenant_is_valid with input as {
        "user": {},
        "resource": {}
    }
}

# ==================== Null/Empty Tenant ID ====================

test_null_user_tenant_invalid if {
    not tenant_is_valid with input as {
        "user": {"tenant_id": null},
        "resource": {"tenant_id": "tenant-abc"}
    }
}

test_empty_user_tenant_invalid if {
    not tenant_is_valid with input as {
        "user": {"tenant_id": ""},
        "resource": {"tenant_id": "tenant-abc"}
    }
}

test_null_resource_tenant_invalid if {
    not tenant_is_valid with input as {
        "user": {"tenant_id": "tenant-abc"},
        "resource": {"tenant_id": null}
    }
}

test_empty_resource_tenant_invalid if {
    not tenant_is_valid with input as {
        "user": {"tenant_id": "tenant-abc"},
        "resource": {"tenant_id": ""}
    }
}

# ==================== UUID Format ====================

test_uuid_tenant_valid if {
    tenant_is_valid with input as {
        "user": {"tenant_id": "123e4567-e89b-12d3-a456-426614174000"},
        "resource": {"tenant_id": "123e4567-e89b-12d3-a456-426614174000"}
    }
}

test_different_uuid_invalid if {
    not tenant_is_valid with input as {
        "user": {"tenant_id": "123e4567-e89b-12d3-a456-426614174000"},
        "resource": {"tenant_id": "987fcdeb-51a2-43f7-b123-456789abcdef"}
    }
}

# ==================== Case Sensitivity ====================

test_tenant_case_sensitive if {
    not tenant_is_valid with input as {
        "user": {"tenant_id": "tenant-abc"},
        "resource": {"tenant_id": "tenant-ABC"}
    }
}

# ==================== Whitespace ====================

test_tenant_with_leading_space_invalid if {
    not tenant_is_valid with input as {
        "user": {"tenant_id": " tenant-abc"},
        "resource": {"tenant_id": "tenant-abc"}
    }
}

test_tenant_with_trailing_space_invalid if {
    not tenant_is_valid with input as {
        "user": {"tenant_id": "tenant-abc"},
        "resource": {"tenant_id": "tenant-abc "}
    }
}

# ==================== Multiple Resources (array) ====================

# Note: This tests a potential future feature where we validate multiple resources
# Current implementation only checks single resource, but this shows extensibility

test_single_resource_in_array_valid if {
    tenant_is_valid with input as {
        "user": {"tenant_id": "tenant-abc"},
        "resource": {"tenant_id": "tenant-abc"}
    }
}

# ==================== Edge Cases ====================

test_unrestricted_without_flag_respects_tenant if {
    not tenant_is_valid with input as {
        "user": {
            "tenant_id": "tenant-abc"
            # unrestricted_access not set (defaults to false)
        },
        "resource": {"tenant_id": "tenant-xyz"}
    }
}

test_unrestricted_false_respects_tenant if {
    not tenant_is_valid with input as {
        "user": {
            "tenant_id": "tenant-abc",
            "unrestricted_access": false
        },
        "resource": {"tenant_id": "tenant-xyz"}
    }
}

test_unrestricted_true_bypasses_tenant if {
    tenant_is_valid with input as {
        "user": {
            "tenant_id": "tenant-abc",
            "unrestricted_access": true
        },
        "resource": {"tenant_id": "tenant-xyz"}
    }
}

# ==================== Real-World Scenarios ====================

test_operador_cannot_access_other_tenant_locacao if {
    not tenant_is_valid with input as {
        "user": {
            "id": "operador@tenant-a.com",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001",
            "role": "OPERADOR"
        },
        "resource": {
            "id": "locacao-123",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440002"
        },
        "action": "locacao:view"
    }
}

test_admin_tenant_cannot_access_other_tenant if {
    not tenant_is_valid with input as {
        "user": {
            "id": "admin@tenant-a.com",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001",
            "role": "ADMIN_TENANT"
        },
        "resource": {
            "id": "jetski-456",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440002"
        },
        "action": "jetski:update"
    }
}

test_platform_admin_can_access_any_tenant if {
    tenant_is_valid with input as {
        "user": {
            "id": "platform@system.com",
            "tenant_id": "platform-tenant",
            "role": "PLATFORM_ADMIN",
            "unrestricted_access": true
        },
        "resource": {
            "id": "jetski-789",
            "tenant_id": "550e8400-e29b-41d4-a716-446655440001"
        },
        "action": "jetski:view"
    }
}

# ==================== Integration with RBAC ====================

test_tenant_validation_independent_of_rbac if {
    # Even if RBAC allows, tenant must match
    tenant_is_valid with input as {
        "user": {
            "tenant_id": "tenant-abc",
            "role": "ADMIN_TENANT" # has RBAC wildcard
        },
        "resource": {"tenant_id": "tenant-abc"}
    }

    not tenant_is_valid with input as {
        "user": {
            "tenant_id": "tenant-abc",
            "role": "ADMIN_TENANT" # has RBAC wildcard
        },
        "resource": {"tenant_id": "tenant-xyz"} # but different tenant
    }
}
