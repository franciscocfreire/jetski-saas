package com.jetski.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.security.TenantAccessInfo;
import com.jetski.usuarios.api.dto.DeactivateMemberResponse;
import com.jetski.usuarios.api.dto.ListMembersResponse;
import com.jetski.usuarios.internal.TenantAccessService;
import com.jetski.usuarios.internal.repository.MembroRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for Member Management operations.
 *
 * Tests:
 * - List members (active only / include inactive)
 * - Plan limit information
 * - Deactivate member (success / validation failures)
 * - Last ADMIN_TENANT protection
 *
 * @author Jetski Team
 * @since 0.5.0
 */
@AutoConfigureMockMvc
class MemberManagementIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MembroRepository membroRepository;

    @Autowired
    private EntityManager entityManager;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    @MockBean
    private TenantAccessService tenantAccessService;

    // Test data UUIDs from V9999__test_data.sql
    private static final UUID TEST_TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ADMIN_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID OPERATOR_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");

    @BeforeEach
    void setUp() {
        // Mock TenantAccessService to allow access for test users (using new provider-based signature)
        TenantAccessInfo adminAccess = TenantAccessInfo.builder()
                .hasAccess(true)
                .roles(List.of("ADMIN_TENANT"))
                .unrestricted(false)
                .build();

        when(tenantAccessService.validateAccess(any(String.class), eq(ADMIN_USER_ID.toString()), eq(TEST_TENANT_ID)))
                .thenReturn(adminAccess);

        // Mock OPA to allow all requests by default
        OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();
        when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Create an active member for testing.
     */
    @org.springframework.transaction.annotation.Transactional
    private UUID createActiveMember(UUID tenantId, String email, String nome, String... roles) {
        UUID usuarioId = UUID.randomUUID();

        // Create usuario
        entityManager.createNativeQuery(
            "INSERT INTO usuario (id, email, nome, ativo, email_verified, created_at, updated_at) " +
            "VALUES (?1, ?2, ?3, true, true, NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC')"
        )
        .setParameter(1, usuarioId)
        .setParameter(2, email)
        .setParameter(3, nome)
        .executeUpdate();

        // Create membro
        entityManager.createNativeQuery(
            "INSERT INTO membro (tenant_id, usuario_id, papeis, ativo, created_at, updated_at) " +
            "VALUES (?1, ?2, ?3, true, NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC')"
        )
        .setParameter(1, tenantId)
        .setParameter(2, usuarioId)
        .setParameter(3, roles)
        .executeUpdate();

        entityManager.flush();
        return usuarioId;
    }

    /**
     * Create an inactive member for testing.
     */
    @org.springframework.transaction.annotation.Transactional
    private UUID createInactiveMember(UUID tenantId, String email, String nome, String... roles) {
        UUID usuarioId = UUID.randomUUID();

        // Create usuario
        entityManager.createNativeQuery(
            "INSERT INTO usuario (id, email, nome, ativo, email_verified, created_at, updated_at) " +
            "VALUES (?1, ?2, ?3, true, true, NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC')"
        )
        .setParameter(1, usuarioId)
        .setParameter(2, email)
        .setParameter(3, nome)
        .executeUpdate();

        // Create membro (inactive)
        entityManager.createNativeQuery(
            "INSERT INTO membro (tenant_id, usuario_id, papeis, ativo, created_at, updated_at) " +
            "VALUES (?1, ?2, ?3, false, NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC')"
        )
        .setParameter(1, tenantId)
        .setParameter(2, usuarioId)
        .setParameter(3, roles)
        .executeUpdate();

        entityManager.flush();
        return usuarioId;
    }

    /**
     * Setup isolated plan with specific user limit for a tenant.
     */
    @org.springframework.transaction.annotation.Transactional
    private void setupIsolatedPlanWithLimit(UUID tenantId, int userLimit) {
        // Deactivate all existing members for this tenant
        entityManager.createNativeQuery(
            "UPDATE membro SET ativo = false WHERE tenant_id = ?1"
        )
        .setParameter(1, tenantId)
        .executeUpdate();

        // Create unique plan with specific limit
        Integer planId = ((Number) entityManager.createNativeQuery("SELECT nextval('plano_id_seq')")
            .getSingleResult()).intValue();

        String limitesJson = String.format("{\"usuarios_max\": %d}", userLimit);

        entityManager.createNativeQuery(
            "INSERT INTO plano (id, nome, limites_json, preco_mensal) " +
            "VALUES (?1, ?2, CAST(?3 AS jsonb), ?4)"
        )
        .setParameter(1, planId)
        .setParameter(2, "Test Plan - Limit " + userLimit)
        .setParameter(3, limitesJson)
        .setParameter(4, 0.0)
        .executeUpdate();

        // Deactivate any existing active subscriptions
        entityManager.createNativeQuery(
            "UPDATE assinatura SET status = 'cancelada' WHERE tenant_id = ?1 AND status = 'ativa'"
        )
        .setParameter(1, tenantId)
        .executeUpdate();

        // Create active subscription
        Integer subscriptionId = ((Number) entityManager.createNativeQuery("SELECT nextval('assinatura_id_seq')")
            .getSingleResult()).intValue();

        entityManager.createNativeQuery(
            "INSERT INTO assinatura (id, tenant_id, plano_id, status, dt_inicio) " +
            "VALUES (?1, ?2, ?3, 'ativa', NOW())"
        )
        .setParameter(1, subscriptionId)
        .setParameter(2, tenantId)
        .setParameter(3, planId)
        .executeUpdate();

        entityManager.flush();
    }

    // ========================================================================
    // Test Cases: List Members
    // ========================================================================

    @Test
    @org.springframework.transaction.annotation.Transactional
    void shouldListActiveMembersOnly() throws Exception {
        // Given - Create test members
        String timestamp = String.valueOf(System.currentTimeMillis());
        createActiveMember(TEST_TENANT_ID, "active1." + timestamp + "@example.com", "Active User 1", "OPERADOR");
        createActiveMember(TEST_TENANT_ID, "active2." + timestamp + "@example.com", "Active User 2", "GERENTE");
        createInactiveMember(TEST_TENANT_ID, "inactive1." + timestamp + "@example.com", "Inactive User 1", "OPERADOR");

        // When - List with includeInactive=false
        String response = mockMvc.perform(get("/v1/tenants/{tenantId}/members", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .param("includeInactive", "false")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.activeCount").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.inactiveCount").value(greaterThanOrEqualTo(1))) // Should count inactive members even when not included in list
                .andExpect(jsonPath("$.members[*].ativo").value(everyItem(is(true))))
                .andReturn().getResponse().getContentAsString();

        // Then - Verify response structure
        ListMembersResponse listResponse = objectMapper.readValue(response, ListMembersResponse.class);
        assertThat(listResponse.getMembers()).allMatch(m -> m.isAtivo());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void shouldListMembersIncludingInactive() throws Exception {
        // Given - Create test members
        String timestamp = String.valueOf(System.currentTimeMillis());
        createActiveMember(TEST_TENANT_ID, "active.inc." + timestamp + "@example.com", "Active User", "OPERADOR");
        createInactiveMember(TEST_TENANT_ID, "inactive.inc." + timestamp + "@example.com", "Inactive User", "OPERADOR");

        // When - List with includeInactive=true
        String response = mockMvc.perform(get("/v1/tenants/{tenantId}/members", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .param("includeInactive", "true")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.activeCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.inactiveCount").value(greaterThanOrEqualTo(1)))
                .andReturn().getResponse().getContentAsString();

        // Then - Verify both active and inactive members are returned
        ListMembersResponse listResponse = objectMapper.readValue(response, ListMembersResponse.class);
        assertThat(listResponse.getMembers()).anyMatch(m -> m.isAtivo());
        assertThat(listResponse.getMembers()).anyMatch(m -> !m.isAtivo());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void shouldIncludePlanLimitInformation() throws Exception {
        // Given - Setup plan with limit of 10 users
        setupIsolatedPlanWithLimit(TEST_TENANT_ID, 10);

        // Create 3 active members
        String timestamp = String.valueOf(System.currentTimeMillis());
        createActiveMember(TEST_TENANT_ID, "limit1." + timestamp + "@example.com", "User 1", "OPERADOR");
        createActiveMember(TEST_TENANT_ID, "limit2." + timestamp + "@example.com", "User 2", "OPERADOR");
        createActiveMember(TEST_TENANT_ID, "limit3." + timestamp + "@example.com", "User 3", "GERENTE");

        // When - List members
        mockMvc.perform(get("/v1/tenants/{tenantId}/members", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .param("includeInactive", "false")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planLimit.maxUsuarios").value(10))
                .andExpect(jsonPath("$.planLimit.currentActive").value(3))
                .andExpect(jsonPath("$.planLimit.available").value(7))
                .andExpect(jsonPath("$.planLimit.limitReached").value(false));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void shouldIndicatePlanLimitReached() throws Exception {
        // Given - Setup plan with limit of 3 users
        setupIsolatedPlanWithLimit(TEST_TENANT_ID, 3);

        // Create exactly 3 active members (at limit)
        String timestamp = String.valueOf(System.currentTimeMillis());
        createActiveMember(TEST_TENANT_ID, "atLimit1." + timestamp + "@example.com", "User 1", "OPERADOR");
        createActiveMember(TEST_TENANT_ID, "atLimit2." + timestamp + "@example.com", "User 2", "OPERADOR");
        createActiveMember(TEST_TENANT_ID, "atLimit3." + timestamp + "@example.com", "User 3", "ADMIN_TENANT");

        // When - List members
        mockMvc.perform(get("/v1/tenants/{tenantId}/members", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .param("includeInactive", "false")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planLimit.maxUsuarios").value(3))
                .andExpect(jsonPath("$.planLimit.currentActive").value(3))
                .andExpect(jsonPath("$.planLimit.available").value(0))
                .andExpect(jsonPath("$.planLimit.limitReached").value(true));
    }

    // ========================================================================
    // Test Cases: Deactivate Member
    // ========================================================================

    @Test
    @org.springframework.transaction.annotation.Transactional
    void shouldDeactivateMemberSuccessfully() throws Exception {
        // Given - Create an active member
        String timestamp = String.valueOf(System.currentTimeMillis());
        UUID usuarioId = createActiveMember(TEST_TENANT_ID, "deactivate." + timestamp + "@example.com", "Deactivate User", "OPERADOR");

        // Verify member is active
        assertThat(membroRepository.findByTenantIdAndUsuarioId(TEST_TENANT_ID, usuarioId))
                .isPresent()
                .get()
                .matches(m -> Boolean.TRUE.equals(m.getAtivo()));

        // When - Deactivate member
        String response = mockMvc.perform(delete("/v1/tenants/{tenantId}/members/{usuarioId}", TEST_TENANT_ID, usuarioId)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(usuarioId.toString()))
                .andExpect(jsonPath("$.email").value("deactivate." + timestamp + "@example.com"))
                .andExpect(jsonPath("$.tenantId").value(TEST_TENANT_ID.toString()))
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        // Then - Verify member is now inactive
        assertThat(membroRepository.findByTenantIdAndUsuarioId(TEST_TENANT_ID, usuarioId))
                .isPresent()
                .get()
                .matches(m -> Boolean.FALSE.equals(m.getAtivo()));

        DeactivateMemberResponse deactivateResponse = objectMapper.readValue(response, DeactivateMemberResponse.class);
        assertThat(deactivateResponse.isSuccess()).isTrue();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void shouldRejectDeactivationOfNonExistentMember() throws Exception {
        // Given - Non-existent usuario ID
        UUID nonExistentUserId = UUID.fromString("99999999-9999-9999-9999-999999999999");

        // When / Then
        mockMvc.perform(delete("/v1/tenants/{tenantId}/members/{usuarioId}", TEST_TENANT_ID, nonExistentUserId)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("não encontrado")));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void shouldRejectDeactivationOfAlreadyInactiveMember() throws Exception {
        // Given - Create an inactive member
        String timestamp = String.valueOf(System.currentTimeMillis());
        UUID usuarioId = createInactiveMember(TEST_TENANT_ID, "already.inactive." + timestamp + "@example.com", "Already Inactive", "OPERADOR");

        // Verify member is already inactive
        assertThat(membroRepository.findByTenantIdAndUsuarioId(TEST_TENANT_ID, usuarioId))
                .isPresent()
                .get()
                .matches(m -> Boolean.FALSE.equals(m.getAtivo()));

        // When / Then
        mockMvc.perform(delete("/v1/tenants/{tenantId}/members/{usuarioId}", TEST_TENANT_ID, usuarioId)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("já está inativo")));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void shouldRejectDeactivationOfLastAdminTenant() throws Exception {
        // Given - Setup isolated tenant with only ONE ADMIN_TENANT
        setupIsolatedPlanWithLimit(TEST_TENANT_ID, 10);
        String timestamp = String.valueOf(System.currentTimeMillis());
        UUID onlyAdminId = createActiveMember(TEST_TENANT_ID, "only.admin." + timestamp + "@example.com", "Only Admin", "ADMIN_TENANT");
        createActiveMember(TEST_TENANT_ID, "operator1." + timestamp + "@example.com", "Operator 1", "OPERADOR");

        // Verify there's only one active ADMIN_TENANT
        long adminCount = membroRepository.countByTenantIdAndPapeisContainingAndAtivo(TEST_TENANT_ID, "ADMIN_TENANT", true);
        assertThat(adminCount).isEqualTo(1);

        // When / Then - Attempt to deactivate the last admin should fail
        mockMvc.perform(delete("/v1/tenants/{tenantId}/members/{usuarioId}", TEST_TENANT_ID, onlyAdminId)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("último ADMIN_TENANT")));

        // Verify admin is still active
        assertThat(membroRepository.findByTenantIdAndUsuarioId(TEST_TENANT_ID, onlyAdminId))
                .isPresent()
                .get()
                .matches(m -> Boolean.TRUE.equals(m.getAtivo()));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void shouldAllowDeactivationWhenMultipleAdminsExist() throws Exception {
        // Given - Setup with TWO ADMIN_TENANT members
        setupIsolatedPlanWithLimit(TEST_TENANT_ID, 10);
        String timestamp = String.valueOf(System.currentTimeMillis());
        UUID admin1Id = createActiveMember(TEST_TENANT_ID, "admin1." + timestamp + "@example.com", "Admin 1", "ADMIN_TENANT");
        UUID admin2Id = createActiveMember(TEST_TENANT_ID, "admin2." + timestamp + "@example.com", "Admin 2", "ADMIN_TENANT");

        // Verify there are 2 active ADMIN_TENANT members
        long adminCount = membroRepository.countByTenantIdAndPapeisContainingAndAtivo(TEST_TENANT_ID, "ADMIN_TENANT", true);
        assertThat(adminCount).isEqualTo(2);

        // When - Deactivate one admin (should succeed)
        mockMvc.perform(delete("/v1/tenants/{tenantId}/members/{usuarioId}", TEST_TENANT_ID, admin1Id)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Then - Verify first admin is inactive, second is still active
        assertThat(membroRepository.findByTenantIdAndUsuarioId(TEST_TENANT_ID, admin1Id))
                .isPresent()
                .get()
                .matches(m -> Boolean.FALSE.equals(m.getAtivo()));

        assertThat(membroRepository.findByTenantIdAndUsuarioId(TEST_TENANT_ID, admin2Id))
                .isPresent()
                .get()
                .matches(m -> Boolean.TRUE.equals(m.getAtivo()));

        // Verify exactly 1 active admin remains
        long remainingAdminCount = membroRepository.countByTenantIdAndPapeisContainingAndAtivo(TEST_TENANT_ID, "ADMIN_TENANT", true);
        assertThat(remainingAdminCount).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void shouldHandleMissingPlanSubscription() throws Exception {
        // Given - Deactivate all subscriptions (no active plan)
        entityManager.createNativeQuery(
            "UPDATE assinatura SET status = 'cancelada' WHERE tenant_id = ?1"
        )
        .setParameter(1, TEST_TENANT_ID)
        .executeUpdate();
        entityManager.flush();

        // When - List members (should fallback to 999 limit)
        mockMvc.perform(get("/v1/tenants/{tenantId}/members", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .param("includeInactive", "false")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planLimit.maxUsuarios").value(999));
    }
}
