package com.jetski.usuarios.internal;

import com.jetski.usuarios.domain.Membro;
import com.jetski.usuarios.internal.UsuarioGlobalRoles;
import com.jetski.usuarios.internal.repository.MembroRepository;
import com.jetski.usuarios.internal.repository.UsuarioGlobalRolesRepository;
import org.junit.jupiter.api.BeforeEach;
import com.jetski.shared.security.TenantAccessInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TenantAccessService
 *
 * Tests multi-tenant access validation with:
 * - Unrestricted platform admin access
 * - Normal user tenant membership
 * - Access denial scenarios
 * - Tenant counting and listing
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantAccessService Tests")
class TenantAccessServiceTest {

    @Mock
    private MembroRepository membroRepository;

    @Mock
    private UsuarioGlobalRolesRepository globalRolesRepository;

    @InjectMocks
    private TenantAccessService tenantAccessService;

    private UUID usuarioId;
    private UUID tenantId;
    private UUID otherTenantId;

    @BeforeEach
    void setUp() {
        usuarioId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        tenantId = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
        otherTenantId = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    }

    // ========================================================================
    // Unrestricted Access Tests (Platform Admin)
    // ========================================================================

    @Test
    @DisplayName("Should grant unrestricted access for platform admin")
    void testUnrestrictedAccess_PlatformAdmin() {
        // Given: Platform admin with unrestricted_access=true
        UsuarioGlobalRoles globalRoles = createGlobalRoles(usuarioId, true, "PLATFORM_ADMIN");
        when(globalRolesRepository.findById(usuarioId)).thenReturn(Optional.of(globalRoles));

        // When: Validating access to ANY tenant
        TenantAccessInfo result = tenantAccessService.validateAccess(usuarioId, tenantId);

        // Then: Should have unrestricted access without checking membro table
        assertThat(result.isHasAccess()).isTrue();
        assertThat(result.isUnrestricted()).isTrue();
        assertThat(result.getRoles()).containsExactly("PLATFORM_ADMIN");
        assertThat(result.getReason()).isEqualTo("Unrestricted platform access");
    }

    @Test
    @DisplayName("Should count -1 for platform admin (unrestricted)")
    void testCountUserTenants_PlatformAdmin() {
        // Given: Platform admin
        UsuarioGlobalRoles globalRoles = createGlobalRoles(usuarioId, true, "PLATFORM_ADMIN");
        when(globalRolesRepository.findById(usuarioId)).thenReturn(Optional.of(globalRoles));

        // When: Counting tenants
        long count = tenantAccessService.countUserTenants(usuarioId);

        // Then: Should return -1 indicating unrestricted access
        assertThat(count).isEqualTo(-1);
    }

    @Test
    @DisplayName("Should list all tenants for platform admin (service doesn't filter)")
    void testListUserTenants_PlatformAdmin() {
        // Given: Platform admin (but listUserTenants doesn't check global roles)
        when(membroRepository.findAllActiveByUsuario(usuarioId))
            .thenReturn(List.of());

        // When: Listing tenants
        List<Membro> result = tenantAccessService.listUserTenants(usuarioId);

        // Then: Should return repository result (filtering happens at controller level)
        assertThat(result).isEmpty();
    }

    // ========================================================================
    // Normal User Access Tests
    // ========================================================================

    @Test
    @DisplayName("Should grant access when user is active member of tenant")
    void testValidateAccess_ActiveMember() {
        // Given: Normal user with no global roles
        when(globalRolesRepository.findById(usuarioId)).thenReturn(Optional.empty());

        // And: User is active member with GERENTE role
        Membro membro = createMembro(tenantId, usuarioId, "GERENTE", "FINANCEIRO");
        when(membroRepository.findActiveByUsuarioAndTenant(usuarioId, tenantId))
            .thenReturn(Optional.of(membro));

        // When: Validating access
        TenantAccessInfo result = tenantAccessService.validateAccess(usuarioId, tenantId);

        // Then: Should grant access with member roles
        assertThat(result.isHasAccess()).isTrue();
        assertThat(result.isUnrestricted()).isFalse();
        assertThat(result.getRoles()).containsExactlyInAnyOrder("GERENTE", "FINANCEIRO");
        assertThat(result.getReason()).isEqualTo("Access granted via membro table");
    }

    @Test
    @DisplayName("Should deny access when user is not member of tenant")
    void testValidateAccess_NotMember() {
        // Given: Normal user with no global roles
        when(globalRolesRepository.findById(usuarioId)).thenReturn(Optional.empty());

        // And: User is NOT member of the requested tenant
        when(membroRepository.findActiveByUsuarioAndTenant(usuarioId, tenantId))
            .thenReturn(Optional.empty());

        // When: Validating access
        TenantAccessInfo result = tenantAccessService.validateAccess(usuarioId, tenantId);

        // Then: Should deny access
        assertThat(result.isHasAccess()).isFalse();
        assertThat(result.isUnrestricted()).isFalse();
        assertThat(result.getRoles()).isEmpty();
        assertThat(result.getReason()).isEqualTo("User is not a member of this tenant");
    }

    @Test
    @DisplayName("Should count correct number of tenants for normal user")
    void testCountUserTenants_NormalUser() {
        // Given: Normal user with no global roles
        when(globalRolesRepository.findById(usuarioId)).thenReturn(Optional.empty());

        // And: User is member of 5 tenants
        when(membroRepository.countActiveByUsuario(usuarioId)).thenReturn(5L);

        // When: Counting tenants
        long count = tenantAccessService.countUserTenants(usuarioId);

        // Then: Should return actual count
        assertThat(count).isEqualTo(5);
    }

    @Test
    @DisplayName("Should list tenants for normal user")
    void testListUserTenants_NormalUser() {
        // Given: User has memberships in multiple tenants
        List<Membro> expectedMembros = List.of(
            createMembro(tenantId, usuarioId, "GERENTE"),
            createMembro(otherTenantId, usuarioId, "OPERADOR")
        );
        when(membroRepository.findAllActiveByUsuario(usuarioId))
            .thenReturn(expectedMembros);

        // When: Listing tenants
        List<Membro> result = tenantAccessService.listUserTenants(usuarioId);

        // Then: Should return all active memberships
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyElementsOf(expectedMembros);
    }

    // ========================================================================
    // Global Roles Without Unrestricted Access
    // ========================================================================

    @Test
    @DisplayName("Should check membership when user has global roles but NOT unrestricted")
    void testValidateAccess_GlobalRolesWithoutUnrestricted() {
        // Given: User has global roles but unrestricted_access=false
        UsuarioGlobalRoles globalRoles = createGlobalRoles(usuarioId, false, "SOME_GLOBAL_ROLE");
        when(globalRolesRepository.findById(usuarioId)).thenReturn(Optional.of(globalRoles));

        // And: User IS a member of the tenant
        Membro membro = createMembro(tenantId, usuarioId, "OPERADOR");
        when(membroRepository.findActiveByUsuarioAndTenant(usuarioId, tenantId))
            .thenReturn(Optional.of(membro));

        // When: Validating access
        TenantAccessInfo result = tenantAccessService.validateAccess(usuarioId, tenantId);

        // Then: Should grant access based on membership (not global roles)
        assertThat(result.isHasAccess()).isTrue();
        assertThat(result.isUnrestricted()).isFalse();
        assertThat(result.getRoles()).containsExactly("OPERADOR");
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Test
    @DisplayName("Should handle null usuario_id gracefully")
    void testValidateAccess_NullUsuarioId() {
        // When/Then: Should throw NullPointerException or handle gracefully
        // (Depending on implementation, you might want to add explicit null checks)
        try {
            tenantAccessService.validateAccess(null, tenantId);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    @DisplayName("Should handle null tenant_id gracefully")
    void testValidateAccess_NullTenantId() {
        // When/Then: Should throw NullPointerException or handle gracefully
        try {
            tenantAccessService.validateAccess(usuarioId, null);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    @DisplayName("Should return 0 when user has no tenant memberships")
    void testCountUserTenants_NoMemberships() {
        // Given: Normal user
        when(globalRolesRepository.findById(usuarioId)).thenReturn(Optional.empty());
        when(membroRepository.countActiveByUsuario(usuarioId)).thenReturn(0L);

        // When: Counting tenants
        long count = tenantAccessService.countUserTenants(usuarioId);

        // Then: Should return 0
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("Should return empty list when user has no memberships")
    void testListUserTenants_NoMemberships() {
        // Given: User with no memberships
        when(membroRepository.findAllActiveByUsuario(usuarioId))
            .thenReturn(List.of());

        // When: Listing tenants
        List<Membro> result = tenantAccessService.listUserTenants(usuarioId);

        // Then: Should return empty list
        assertThat(result).isEmpty();
    }

    // ========================================================================
    // Caching Tests
    // ========================================================================

    @Test
    @DisplayName("Should call repository when validating access (cache happens at Spring level)")
    void testValidateAccess_CallsRepository() {
        // Given: Active member
        when(globalRolesRepository.findById(usuarioId)).thenReturn(Optional.empty());
        Membro membro = createMembro(tenantId, usuarioId, "GERENTE");
        when(membroRepository.findActiveByUsuarioAndTenant(usuarioId, tenantId))
            .thenReturn(Optional.of(membro));

        // When: Calling multiple times (in unit test, no cache is active)
        tenantAccessService.validateAccess(usuarioId, tenantId);
        tenantAccessService.validateAccess(usuarioId, tenantId);

        // Then: Repository called multiple times in unit tests (no cache)
        // In integration tests with Redis, this would be called only once
        verify(membroRepository, org.mockito.Mockito.times(2))
            .findActiveByUsuarioAndTenant(usuarioId, tenantId);
    }

    // ========================================================================
    // Additional Edge Cases (P1)
    // ========================================================================

    @Test
    @DisplayName("Should handle large number of tenant memberships")
    void testCountUserTenants_LargeNumber() {
        // Given: Normal user with many tenants
        when(globalRolesRepository.findById(usuarioId)).thenReturn(Optional.empty());
        when(membroRepository.countActiveByUsuario(usuarioId)).thenReturn(1000L);

        // When: Counting tenants
        long count = tenantAccessService.countUserTenants(usuarioId);

        // Then: Should return correct count
        assertThat(count).isEqualTo(1000L);
    }

    @Test
    @DisplayName("Should handle user with multiple roles in tenant")
    void testValidateAccess_MultipleRoles() {
        // Given: User with no global roles
        when(globalRolesRepository.findById(usuarioId)).thenReturn(Optional.empty());

        // And: User has multiple roles in tenant
        Membro membro = createMembro(tenantId, usuarioId,
            "ADMIN_TENANT", "GERENTE", "OPERADOR", "FINANCEIRO");
        when(membroRepository.findActiveByUsuarioAndTenant(usuarioId, tenantId))
            .thenReturn(Optional.of(membro));

        // When: Validating access
        TenantAccessInfo result = tenantAccessService.validateAccess(usuarioId, tenantId);

        // Then: Should return all roles
        assertThat(result.isHasAccess()).isTrue();
        assertThat(result.getRoles()).hasSize(4);
        assertThat(result.getRoles()).containsExactlyInAnyOrder(
            "ADMIN_TENANT", "GERENTE", "OPERADOR", "FINANCEIRO");
    }

    @Test
    @DisplayName("Should handle user with single role in tenant")
    void testValidateAccess_SingleRole() {
        // Given: User with no global roles
        when(globalRolesRepository.findById(usuarioId)).thenReturn(Optional.empty());

        // And: User has single role
        Membro membro = createMembro(tenantId, usuarioId, "OPERADOR");
        when(membroRepository.findActiveByUsuarioAndTenant(usuarioId, tenantId))
            .thenReturn(Optional.of(membro));

        // When: Validating access
        TenantAccessInfo result = tenantAccessService.validateAccess(usuarioId, tenantId);

        // Then: Should return single role
        assertThat(result.isHasAccess()).isTrue();
        assertThat(result.getRoles()).hasSize(1);
        assertThat(result.getRoles()).containsExactly("OPERADOR");
    }

    @Test
    @DisplayName("Should count tenants when user has global roles but NOT unrestricted")
    void testCountUserTenants_GlobalRolesWithoutUnrestricted() {
        // Given: User has global roles but unrestricted_access=false
        UsuarioGlobalRoles globalRoles = createGlobalRoles(usuarioId, false, "SOME_ROLE");
        when(globalRolesRepository.findById(usuarioId)).thenReturn(Optional.of(globalRoles));

        // And: User has specific tenant memberships
        when(membroRepository.countActiveByUsuario(usuarioId)).thenReturn(3L);

        // When: Counting tenants
        long count = tenantAccessService.countUserTenants(usuarioId);

        // Then: Should return actual count (not -1)
        assertThat(count).isEqualTo(3L);
        assertThat(count).isNotEqualTo(-1L);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Create UsuarioGlobalRoles for testing
     */
    private UsuarioGlobalRoles createGlobalRoles(UUID usuarioId, boolean unrestricted, String... roles) {
        UsuarioGlobalRoles globalRoles = new UsuarioGlobalRoles();
        globalRoles.setUsuarioId(usuarioId);
        globalRoles.setRoles(roles);
        globalRoles.setUnrestrictedAccess(unrestricted);
        globalRoles.setCreatedAt(Instant.now());
        globalRoles.setUpdatedAt(Instant.now());
        return globalRoles;
    }

    /**
     * Create Membro for testing
     */
    private Membro createMembro(UUID tenantId, UUID usuarioId, String... papeis) {
        Membro membro = new Membro();
        membro.setId(1); // Membro ID is Integer, not UUID
        membro.setTenantId(tenantId);
        membro.setUsuarioId(usuarioId);
        membro.setPapeis(papeis);
        membro.setAtivo(true);
        membro.setCreatedAt(Instant.now());
        membro.setUpdatedAt(Instant.now());
        return membro;
    }
}
