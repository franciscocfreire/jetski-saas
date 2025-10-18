package com.jetski.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TenantAccessInfo
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@DisplayName("TenantAccessInfo Tests")
class TenantAccessInfoTest {

    // ========================================================================
    // Factory Method Tests
    // ========================================================================

    @Test
    @DisplayName("denied() should create denied access info with reason")
    void deniedShouldCreateDeniedAccessInfo() {
        // Given
        String reason = "User is not a member of this tenant";

        // When
        TenantAccessInfo info = TenantAccessInfo.denied(reason);

        // Then
        assertThat(info).isNotNull();
        assertThat(info.isHasAccess()).isFalse();
        assertThat(info.getRoles()).isEmpty();
        assertThat(info.isUnrestricted()).isFalse();
        assertThat(info.getReason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("allowed() should create allowed access info with roles")
    void allowedShouldCreateAllowedAccessInfo() {
        // Given
        List<String> roles = List.of("GERENTE", "OPERADOR");

        // When
        TenantAccessInfo info = TenantAccessInfo.allowed(roles);

        // Then
        assertThat(info).isNotNull();
        assertThat(info.isHasAccess()).isTrue();
        assertThat(info.getRoles()).containsExactlyElementsOf(roles);
        assertThat(info.isUnrestricted()).isFalse();
        assertThat(info.getReason()).isEqualTo("Access granted via membro table");
    }

    @Test
    @DisplayName("unrestricted() should create unrestricted access info with global roles")
    void unrestrictedShouldCreateUnrestrictedAccessInfo() {
        // Given
        List<String> globalRoles = List.of("PLATFORM_ADMIN");

        // When
        TenantAccessInfo info = TenantAccessInfo.unrestricted(globalRoles);

        // Then
        assertThat(info).isNotNull();
        assertThat(info.isHasAccess()).isTrue();
        assertThat(info.getRoles()).containsExactlyElementsOf(globalRoles);
        assertThat(info.isUnrestricted()).isTrue();
        assertThat(info.getReason()).isEqualTo("Unrestricted platform access");
    }

    // ========================================================================
    // Builder Pattern Tests
    // ========================================================================

    @Test
    @DisplayName("builder() should create custom access info")
    void builderShouldCreateCustomAccessInfo() {
        // When
        TenantAccessInfo info = TenantAccessInfo.builder()
            .hasAccess(true)
            .roles(List.of("ADMIN_TENANT", "FINANCEIRO"))
            .unrestricted(false)
            .reason("Custom access granted")
            .build();

        // Then
        assertThat(info.isHasAccess()).isTrue();
        assertThat(info.getRoles()).containsExactly("ADMIN_TENANT", "FINANCEIRO");
        assertThat(info.isUnrestricted()).isFalse();
        assertThat(info.getReason()).isEqualTo("Custom access granted");
    }

    @Test
    @DisplayName("builder() should allow building with minimal fields")
    void builderShouldAllowMinimalFields() {
        // When
        TenantAccessInfo info = TenantAccessInfo.builder()
            .hasAccess(false)
            .build();

        // Then
        assertThat(info.isHasAccess()).isFalse();
        assertThat(info.getRoles()).isNull();
        assertThat(info.isUnrestricted()).isFalse();
        assertThat(info.getReason()).isNull();
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Test
    @DisplayName("should handle empty roles list")
    void shouldHandleEmptyRolesList() {
        // When
        TenantAccessInfo info = TenantAccessInfo.allowed(List.of());

        // Then
        assertThat(info.getRoles()).isEmpty();
        assertThat(info.isHasAccess()).isTrue();
    }

    @Test
    @DisplayName("should handle single role")
    void shouldHandleSingleRole() {
        // When
        TenantAccessInfo info = TenantAccessInfo.allowed(List.of("VENDEDOR"));

        // Then
        assertThat(info.getRoles()).containsExactly("VENDEDOR");
    }

    @Test
    @DisplayName("should handle multiple roles")
    void shouldHandleMultipleRoles() {
        // Given
        List<String> multipleRoles = List.of(
            "ADMIN_TENANT",
            "GERENTE",
            "OPERADOR",
            "VENDEDOR",
            "FINANCEIRO",
            "MECANICO"
        );

        // When
        TenantAccessInfo info = TenantAccessInfo.allowed(multipleRoles);

        // Then
        assertThat(info.getRoles()).hasSize(6);
        assertThat(info.getRoles()).containsExactlyElementsOf(multipleRoles);
    }

    @Test
    @DisplayName("denied() should handle null reason")
    void deniedShouldHandleNullReason() {
        // When
        TenantAccessInfo info = TenantAccessInfo.denied(null);

        // Then
        assertThat(info.isHasAccess()).isFalse();
        assertThat(info.getReason()).isNull();
    }

    @Test
    @DisplayName("denied() should handle empty reason")
    void deniedShouldHandleEmptyReason() {
        // When
        TenantAccessInfo info = TenantAccessInfo.denied("");

        // Then
        assertThat(info.isHasAccess()).isFalse();
        assertThat(info.getReason()).isEmpty();
    }

    @Test
    @DisplayName("unrestricted() should handle empty global roles")
    void unrestrictedShouldHandleEmptyGlobalRoles() {
        // When
        TenantAccessInfo info = TenantAccessInfo.unrestricted(List.of());

        // Then
        assertThat(info.isUnrestricted()).isTrue();
        assertThat(info.getRoles()).isEmpty();
    }

    // ========================================================================
    // Getter/Setter Tests
    // ========================================================================

    @Test
    @DisplayName("setters should update fields correctly")
    void settersShouldUpdateFields() {
        // Given
        TenantAccessInfo info = new TenantAccessInfo();

        // When
        info.setHasAccess(true);
        info.setRoles(List.of("TEST_ROLE"));
        info.setUnrestricted(true);
        info.setReason("Test reason");

        // Then
        assertThat(info.isHasAccess()).isTrue();
        assertThat(info.getRoles()).containsExactly("TEST_ROLE");
        assertThat(info.isUnrestricted()).isTrue();
        assertThat(info.getReason()).isEqualTo("Test reason");
    }

    @Test
    @DisplayName("no-args constructor should create empty instance")
    void noArgsConstructorShouldCreateEmptyInstance() {
        // When
        TenantAccessInfo info = new TenantAccessInfo();

        // Then
        assertThat(info.isHasAccess()).isFalse();
        assertThat(info.getRoles()).isNull();
        assertThat(info.isUnrestricted()).isFalse();
        assertThat(info.getReason()).isNull();
    }

    @Test
    @DisplayName("all-args constructor should set all fields")
    void allArgsConstructorShouldSetAllFields() {
        // Given
        List<String> roles = List.of("ROLE1", "ROLE2");

        // When
        TenantAccessInfo info = new TenantAccessInfo(
            true,
            roles,
            false,
            "All args constructor test"
        );

        // Then
        assertThat(info.isHasAccess()).isTrue();
        assertThat(info.getRoles()).isEqualTo(roles);
        assertThat(info.isUnrestricted()).isFalse();
        assertThat(info.getReason()).isEqualTo("All args constructor test");
    }

    // ========================================================================
    // Equals/HashCode Tests (Lombok @Data)
    // ========================================================================

    @Test
    @DisplayName("equals() should return true for identical objects")
    void equalsShouldReturnTrueForIdenticalObjects() {
        // Given
        TenantAccessInfo info1 = TenantAccessInfo.allowed(List.of("ROLE1"));
        TenantAccessInfo info2 = TenantAccessInfo.allowed(List.of("ROLE1"));

        // Then
        assertThat(info1).isEqualTo(info2);
        assertThat(info1.hashCode()).isEqualTo(info2.hashCode());
    }

    @Test
    @DisplayName("equals() should return false for different objects")
    void equalsShouldReturnFalseForDifferentObjects() {
        // Given
        TenantAccessInfo info1 = TenantAccessInfo.allowed(List.of("ROLE1"));
        TenantAccessInfo info2 = TenantAccessInfo.denied("Access denied");

        // Then
        assertThat(info1).isNotEqualTo(info2);
    }

    @Test
    @DisplayName("toString() should return string representation")
    void toStringShouldReturnStringRepresentation() {
        // Given
        TenantAccessInfo info = TenantAccessInfo.allowed(List.of("GERENTE"));

        // When
        String str = info.toString();

        // Then
        assertThat(str).contains("TenantAccessInfo");
        assertThat(str).contains("hasAccess=true");
        assertThat(str).contains("GERENTE");
    }

    // ========================================================================
    // Real-world Scenario Tests
    // ========================================================================

    @Test
    @DisplayName("should represent typical denied scenario")
    void shouldRepresentTypicalDeniedScenario() {
        // When
        TenantAccessInfo info = TenantAccessInfo.denied(
            "User 123 is not a member of tenant abc-456"
        );

        // Then
        assertThat(info.isHasAccess()).isFalse();
        assertThat(info.getRoles()).isEmpty();
        assertThat(info.isUnrestricted()).isFalse();
        assertThat(info.getReason()).contains("not a member");
    }

    @Test
    @DisplayName("should represent typical manager access scenario")
    void shouldRepresentTypicalManagerAccessScenario() {
        // When
        TenantAccessInfo info = TenantAccessInfo.allowed(
            List.of("GERENTE", "OPERADOR")
        );

        // Then
        assertThat(info.isHasAccess()).isTrue();
        assertThat(info.getRoles()).contains("GERENTE", "OPERADOR");
        assertThat(info.isUnrestricted()).isFalse();
    }

    @Test
    @DisplayName("should represent platform admin unrestricted scenario")
    void shouldRepresentPlatformAdminScenario() {
        // When
        TenantAccessInfo info = TenantAccessInfo.unrestricted(
            List.of("PLATFORM_ADMIN", "SUPER_USER")
        );

        // Then
        assertThat(info.isHasAccess()).isTrue();
        assertThat(info.isUnrestricted()).isTrue();
        assertThat(info.getRoles()).contains("PLATFORM_ADMIN", "SUPER_USER");
        assertThat(info.getReason()).isEqualTo("Unrestricted platform access");
    }
}
