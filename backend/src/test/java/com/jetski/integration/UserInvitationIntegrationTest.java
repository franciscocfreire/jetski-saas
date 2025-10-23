package com.jetski.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.email.EmailService;
import com.jetski.shared.security.UserProvisioningService;
import com.jetski.shared.security.TenantAccessInfo;
import com.jetski.usuarios.api.dto.InviteUserRequest;
import com.jetski.usuarios.domain.Convite;
import com.jetski.usuarios.internal.repository.ConviteRepository;
import com.jetski.usuarios.internal.repository.MembroRepository;
import com.jetski.usuarios.internal.TenantAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for User Invitation flow.
 *
 * Tests:
 * - Success scenarios (new user, multiple roles, different roles)
 * - Validation (email, name, roles)
 * - Business rules (duplicate, plan limits, invalid tenant)
 * - Authorization (authentication, roles, cross-tenant)
 * - Integration (email, Keycloak, database)
 *
 * @author Jetski Team
 * @since 0.4.0
 */
@AutoConfigureMockMvc
class UserInvitationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConviteRepository conviteRepository;

    @Autowired
    private MembroRepository membroRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    @MockBean
    private TenantAccessService tenantAccessService;

    @SpyBean
    private EmailService emailService;

    @MockBean
    private UserProvisioningService userProvisioningService;

    // Test data UUIDs from V9999__test_data.sql
    private static final UUID TEST_TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ADMIN_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ADMIN_ID = UUID.fromString("10000000-0000-0000-0000-000000000099");

    // Keycloak UUID (not in usuario table) - simulates real-world scenario
    private static final UUID KEYCLOAK_ADMIN_UUID = UUID.fromString("b71dbb6a-52be-42b9-bf1e-074fc62e7e40");

    @BeforeEach
    void setUp() {
        // Mock TenantAccessService to allow access for test users (using new provider-based signature)
        TenantAccessInfo adminAccess = TenantAccessInfo.builder()
                .hasAccess(true)
                .roles(List.of("ADMIN_TENANT"))
                .unrestricted(false)
                .usuarioId(ADMIN_USER_ID)
                .build();

        when(tenantAccessService.validateAccess(any(String.class), eq(ADMIN_USER_ID.toString()), eq(TEST_TENANT_ID)))
                .thenReturn(adminAccess);

        TenantAccessInfo otherAdminAccess = TenantAccessInfo.builder()
                .hasAccess(true)
                .roles(List.of("ADMIN_TENANT"))
                .unrestricted(false)
                .usuarioId(OTHER_ADMIN_ID)
                .build();

        when(tenantAccessService.validateAccess(any(String.class), eq(OTHER_ADMIN_ID.toString()), eq(OTHER_TENANT_ID)))
                .thenReturn(otherAdminAccess);

        // Mock access for Keycloak UUID (simulates JWT with Keycloak subject)
        // CRITICAL: The usuarioId must be the PostgreSQL UUID (ADMIN_USER_ID), NOT the Keycloak UUID
        TenantAccessInfo keycloakAdminAccess = TenantAccessInfo.builder()
                .hasAccess(true)
                .roles(List.of("ADMIN_TENANT"))
                .unrestricted(false)
                .usuarioId(ADMIN_USER_ID)  // Maps Keycloak UUID to PostgreSQL UUID
                .build();

        when(tenantAccessService.validateAccess(any(String.class), eq(KEYCLOAK_ADMIN_UUID.toString()), eq(TEST_TENANT_ID)))
                .thenReturn(keycloakAdminAccess);

        // Deny cross-tenant access (using new provider-based signature)
        TenantAccessInfo deniedAccess = TenantAccessInfo.builder()
                .hasAccess(false)
                .reason("User is not a member of this tenant")
                .build();

        when(tenantAccessService.validateAccess(any(String.class), eq(ADMIN_USER_ID.toString()), eq(OTHER_TENANT_ID)))
                .thenReturn(deniedAccess);

        // Mock OPA to allow all requests by default
        OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();
        when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

        // Mock email service (don't actually send emails in tests)
        doNothing().when(emailService).sendInvitationEmail(anyString(), anyString(), anyString(), anyString());
    }

    // ========================================================================
    // Helper Methods - Isolated Plan Setup (Phase 3)
    // ========================================================================

    /**
     * Setup isolated plan with specific user limit for a tenant.
     * Creates a new plan and active subscription, replacing any existing subscription.
     *
     * This ensures test isolation - changes to global test data won't affect this test.
     *
     * @param tenantId tenant to configure
     * @param userLimit maximum users allowed
     */
    @org.springframework.transaction.annotation.Transactional
    private void setupIsolatedPlanWithLimit(UUID tenantId, int userLimit) {
        // 0. Deactivate all existing members for this tenant to ensure clean state
        entityManager.createNativeQuery(
            "UPDATE membro SET ativo = false WHERE tenant_id = ?1"
        )
        .setParameter(1, tenantId)
        .executeUpdate();

        // 1. Create unique plan with specific limit (Note: plano.id is INTEGER, not UUID)
        // Get next ID from sequence
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

        // 2. Deactivate any existing active subscriptions for this tenant
        entityManager.createNativeQuery(
            "UPDATE assinatura SET status = 'cancelada' WHERE tenant_id = ?1 AND status = 'ativa'"
        )
        .setParameter(1, tenantId)
        .executeUpdate();

        // 3. Create active subscription linking tenant to new plan
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

    /**
     * Create an active member for testing plan limits.
     * Creates both usuario and membro records.
     *
     * @param tenantId tenant ID
     * @param email member email
     * @return created usuario ID
     */
    @org.springframework.transaction.annotation.Transactional
    private UUID createActiveMember(UUID tenantId, String email) {
        UUID usuarioId = UUID.randomUUID();

        // Create usuario
        entityManager.createNativeQuery(
            "INSERT INTO usuario (id, email, nome, ativo, email_verified, created_at, updated_at) " +
            "VALUES (?1, ?2, ?3, true, true, NOW(), NOW())"
        )
        .setParameter(1, usuarioId)
        .setParameter(2, email)
        .setParameter(3, "Test Member - " + email)
        .executeUpdate();

        // Create membro (tenant membership)
        entityManager.createNativeQuery(
            "INSERT INTO membro (tenant_id, usuario_id, papeis, ativo, created_at, updated_at) " +
            "VALUES (?1, ?2, ?3, true, NOW(), NOW())"
        )
        .setParameter(1, tenantId)
        .setParameter(2, usuarioId)
        .setParameter(3, new String[]{"OPERADOR"})
        .executeUpdate();

        entityManager.flush();
        return usuarioId;
    }

    // ========================================================================
    // Success Scenarios
    // ========================================================================

    @Test
    void shouldInviteNewUserSuccessfully() throws Exception {
        // Given
        InviteUserRequest request = InviteUserRequest.builder()
                .email("new.user@example.com")
                .nome("New User")
                .papeis(new String[]{"OPERADOR"})
                .build();

        Instant beforeInvite = Instant.now();

        // When
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conviteId").exists())
                .andExpect(jsonPath("$.email").value("new.user@example.com"))
                .andExpect(jsonPath("$.nome").value("New User"))
                .andExpect(jsonPath("$.papeis[0]").value("OPERADOR"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.message").value(containsString("48 horas")));

        Instant afterInvite = Instant.now();

        // Then - verify database record
        Convite convite = conviteRepository.findByTenantIdAndEmail(TEST_TENANT_ID, "new.user@example.com").get();
        assertThat(convite.getEmail()).isEqualTo("new.user@example.com");
        assertThat(convite.getNome()).isEqualTo("New User");
        assertThat(convite.getPapeis()).containsExactly("OPERADOR");
        assertThat(convite.getStatus()).isEqualTo(Convite.ConviteStatus.PENDING);
        assertThat(convite.getToken()).isNotEmpty();
        assertThat(convite.getExpiresAt()).isAfter(beforeInvite.plusSeconds(172700)); // ~48h - 100s margin
        assertThat(convite.getExpiresAt()).isBefore(afterInvite.plusSeconds(172900)); // ~48h + 100s margin
        assertThat(convite.getCreatedBy()).isEqualTo(ADMIN_USER_ID);

        // Verify email was sent
        verify(emailService).sendInvitationEmail(
                eq("new.user@example.com"),
                eq("New User"),
                anyString(),
                anyString()  // Temporary password (generated randomly)
        );
    }

    /**
     * RED PHASE - TDD Test for FK Violation Bug
     *
     * This test simulates the real-world scenario where:
     * 1. JWT subject contains Keycloak UUID (not PostgreSQL UUID)
     * 2. Controller tries to use this UUID directly as created_by
     * 3. FK constraint violation occurs because Keycloak UUID doesn't exist in usuario table
     *
     * EXPECTED: Test should PASS (invitation created successfully)
     * CURRENT: Test will FAIL with DataIntegrityViolationException (FK violation)
     *
     * After implementing the fix (TenantContext.getUsuarioId()), this test will pass.
     */
    @Test
    void shouldInviteUserWithKeycloakJWT() throws Exception {
        // Given - JWT with Keycloak UUID as subject (not in usuario table)
        InviteUserRequest request = InviteUserRequest.builder()
                .email("keycloak.jwt@example.com")
                .nome("Keycloak JWT User")
                .papeis(new String[]{"OPERADOR"})
                .build();

        Instant beforeInvite = Instant.now();

        // When - JWT subject is Keycloak UUID (this simulates real authentication)
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(KEYCLOAK_ADMIN_UUID.toString())))) // <-- Keycloak UUID!
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conviteId").exists())
                .andExpect(jsonPath("$.email").value("keycloak.jwt@example.com"))
                .andExpect(jsonPath("$.nome").value("Keycloak JWT User"))
                .andExpect(jsonPath("$.papeis[0]").value("OPERADOR"));

        Instant afterInvite = Instant.now();

        // Then - verify created_by should be PostgreSQL ADMIN_USER_ID (not Keycloak UUID)
        Convite convite = conviteRepository.findByTenantIdAndEmail(TEST_TENANT_ID, "keycloak.jwt@example.com").get();
        assertThat(convite.getEmail()).isEqualTo("keycloak.jwt@example.com");
        assertThat(convite.getNome()).isEqualTo("Keycloak JWT User");
        assertThat(convite.getPapeis()).containsExactly("OPERADOR");
        assertThat(convite.getStatus()).isEqualTo(Convite.ConviteStatus.PENDING);
        assertThat(convite.getToken()).isNotEmpty();
        assertThat(convite.getExpiresAt()).isAfter(beforeInvite.plusSeconds(172700));
        assertThat(convite.getExpiresAt()).isBefore(afterInvite.plusSeconds(172900));

        // CRITICAL: created_by must be PostgreSQL UUID (ADMIN_USER_ID), NOT Keycloak UUID
        // This will fail until we implement TenantContext.getUsuarioId()
        assertThat(convite.getCreatedBy()).isEqualTo(ADMIN_USER_ID);

        // Verify email was sent
        verify(emailService).sendInvitationEmail(
                eq("keycloak.jwt@example.com"),
                eq("Keycloak JWT User"),
                anyString(),
                anyString()  // Temporary password (generated randomly)
        );
    }

    @Test
    void shouldInviteUserWithMultipleRoles() throws Exception {
        // Given
        InviteUserRequest request = InviteUserRequest.builder()
                .email("multi.roles@example.com")
                .nome("Multi Role User")
                .papeis(new String[]{"GERENTE", "OPERADOR", "VENDEDOR"})
                .build();

        // When
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papeis", hasSize(3)))
                .andExpect(jsonPath("$.papeis", containsInAnyOrder("GERENTE", "OPERADOR", "VENDEDOR")));

        // Then - verify all roles are persisted
        Convite convite = conviteRepository.findByTenantIdAndEmail(TEST_TENANT_ID, "multi.roles@example.com").get();
        assertThat(convite.getPapeis()).containsExactlyInAnyOrder("GERENTE", "OPERADOR", "VENDEDOR");
    }

    @Test
    void shouldCreateDatabaseRecordWithAllFields() throws Exception {
        // Given
        InviteUserRequest request = InviteUserRequest.builder()
                .email("full.test@example.com")
                .nome("Full Test User")
                .papeis(new String[]{"MECANICO"})
                .build();

        // When
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isOk());

        // Then - verify all database fields
        Convite convite = conviteRepository.findByTenantIdAndEmail(TEST_TENANT_ID, "full.test@example.com").get();

        assertThat(convite.getId()).isNotNull();
        assertThat(convite.getTenantId()).isEqualTo(TEST_TENANT_ID);
        assertThat(convite.getEmail()).isEqualTo("full.test@example.com");
        assertThat(convite.getNome()).isEqualTo("Full Test User");
        assertThat(convite.getPapeis()).containsExactly("MECANICO");
        assertThat(convite.getToken()).hasSize(40); // 40 chars as per implementation
        assertThat(convite.getExpiresAt()).isNotNull();
        assertThat(convite.getCreatedBy()).isEqualTo(ADMIN_USER_ID);
        assertThat(convite.getActivatedAt()).isNull();
        assertThat(convite.getUsuarioId()).isNull();
        assertThat(convite.getStatus()).isEqualTo(Convite.ConviteStatus.PENDING);
        assertThat(convite.getCreatedAt()).isNotNull();
        assertThat(convite.getUpdatedAt()).isNotNull();
    }

    // ========================================================================
    // Validation Scenarios
    // ========================================================================

    @Test
    void shouldRejectInvitationWithoutEmail() throws Exception {
        // Given
        InviteUserRequest request = InviteUserRequest.builder()
                .nome("Test User")
                .papeis(new String[]{"OPERADOR"})
                .build();

        // When / Then
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void shouldRejectInvitationWithInvalidEmail() throws Exception {
        // Given
        InviteUserRequest request = InviteUserRequest.builder()
                .email("invalid-email-format")
                .nome("Test User")
                .papeis(new String[]{"OPERADOR"})
                .build();

        // When / Then
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void shouldRejectInvitationWithoutName() throws Exception {
        // Given
        InviteUserRequest request = InviteUserRequest.builder()
                .email("test@example.com")
                .papeis(new String[]{"OPERADOR"})
                .build();

        // When / Then
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nome").exists());
    }

    @Test
    void shouldRejectInvitationWithShortName() throws Exception {
        // Given
        InviteUserRequest request = InviteUserRequest.builder()
                .email("test@example.com")
                .nome("A") // Only 1 char, minimum is 2
                .papeis(new String[]{"OPERADOR"})
                .build();

        // When / Then
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nome").exists());
    }

    @Test
    void shouldRejectInvitationWithoutRoles() throws Exception {
        // Given
        InviteUserRequest request = InviteUserRequest.builder()
                .email("test@example.com")
                .nome("Test User")
                .papeis(new String[]{}) // Empty roles array
                .build();

        // When / Then
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.papeis").exists());
    }

    // ========================================================================
    // Business Rule Violations
    // ========================================================================

    @Test
    void shouldRejectDuplicatePendingInvitation() throws Exception {
        // Given - invitation already exists from test data
        InviteUserRequest request = InviteUserRequest.builder()
                .email("pending.user@example.com") // Already has PENDING invitation
                .nome("Pending User")
                .papeis(new String[]{"OPERADOR"})
                .build();

        // When / Then
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("convite pendente")));
    }

    @Test
    void shouldAllowReInvitingExpiredInvitation() throws Exception {
        // Given - user with EXPIRED invitation
        InviteUserRequest request = InviteUserRequest.builder()
                .email("expired.user@example.com") // Has EXPIRED invitation
                .nome("Expired User Retry")
                .papeis(new String[]{"OPERADOR"})
                .build();

        // When - should succeed (can re-invite expired)
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("expired.user@example.com"));

        // Then - verify new invitation was created with PENDING status
        // Note: After migration V1006, multiple invitations can exist for same email (one EXPIRED, one PENDING)
        List<Convite> convites = conviteRepository.findByTenantIdAndStatus(TEST_TENANT_ID, Convite.ConviteStatus.PENDING);
        Convite pendingConvite = convites.stream()
                .filter(c -> c.getEmail().equals("expired.user@example.com"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No PENDING invitation found for expired.user@example.com"));

        assertThat(pendingConvite.getStatus()).isEqualTo(Convite.ConviteStatus.PENDING);
        assertThat(pendingConvite.getEmail()).isEqualTo("expired.user@example.com");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void shouldRejectInvitationWhenPlanLimitReached() throws Exception {
        // Given - Setup isolated plan with limit of 2 users (YOUR SUGGESTION!)
        // This ensures test won't break if global test data changes
        setupIsolatedPlanWithLimit(TEST_TENANT_ID, 2);

        // Create 2 active members (now at 2/2 capacity - LIMIT REACHED!)
        // NOTE: Invitations don't count as members - only activated members count!
        String uniqueTimestamp = String.valueOf(System.currentTimeMillis());
        createActiveMember(TEST_TENANT_ID, "member.1." + uniqueTimestamp + "@example.com");
        createActiveMember(TEST_TENANT_ID, "member.2." + uniqueTimestamp + "@example.com");

        // When - Try to invite another user - should FAIL 403 (would exceed limit)
        InviteUserRequest request = InviteUserRequest.builder()
                .email("limit.exceeded." + uniqueTimestamp + "@example.com")
                .nome("Limit Exceeded User")
                .papeis(new String[]{"OPERADOR"})
                .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message", containsString("Limite")));
    }

    @Test
    void shouldRejectInvitationToNonExistentTenant() throws Exception {
        // Given
        UUID nonExistentTenant = UUID.fromString("99999999-9999-9999-9999-999999999999");
        InviteUserRequest request = InviteUserRequest.builder()
                .email("test@example.com")
                .nome("Test User")
                .papeis(new String[]{"OPERADOR"})
                .build();

        // Mock - user doesn't have access to non-existent tenant (using new provider-based signature)
        TenantAccessInfo deniedAccess = TenantAccessInfo.builder()
                .hasAccess(false)
                .reason("Tenant not found")
                .build();
        when(tenantAccessService.validateAccess(any(String.class), eq(ADMIN_USER_ID.toString()), eq(nonExistentTenant)))
                .thenReturn(deniedAccess);

        // When / Then
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", nonExistentTenant)
                        .header("X-Tenant-Id", nonExistentTenant.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isForbidden());
    }

    // ========================================================================
    // Authorization Scenarios
    // ========================================================================

    @Test
    void shouldRejectInvitationWithoutAuthentication() throws Exception {
        // Given
        InviteUserRequest request = InviteUserRequest.builder()
                .email("test@example.com")
                .nome("Test User")
                .papeis(new String[]{"OPERADOR"})
                .build();

        // When / Then
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInvitationWithoutTenantHeader() throws Exception {
        // Given
        InviteUserRequest request = InviteUserRequest.builder()
                .email("test@example.com")
                .nome("Test User")
                .papeis(new String[]{"OPERADOR"})
                .build();

        // When / Then
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Tenant ID not found")));
    }

    @Test
    void shouldRejectCrossTenantInvitation() throws Exception {
        // Given - trying to invite to OTHER_TENANT but authenticated as TEST_TENANT admin
        InviteUserRequest request = InviteUserRequest.builder()
                .email("cross.tenant@example.com")
                .nome("Cross Tenant User")
                .papeis(new String[]{"OPERADOR"})
                .build();

        // When / Then
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", OTHER_TENANT_ID)
                        .header("X-Tenant-Id", OTHER_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("No access")));
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Test
    void shouldHandleSpecialCharactersInName() throws Exception {
        // Given
        InviteUserRequest request = InviteUserRequest.builder()
                .email("special.chars@example.com")
                .nome("José O'Brien-Silva")
                .papeis(new String[]{"OPERADOR"})
                .build();

        // When
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("José O'Brien-Silva"));

        // Then
        Convite convite = conviteRepository.findByTenantIdAndEmail(TEST_TENANT_ID, "special.chars@example.com").get();
        assertThat(convite.getNome()).isEqualTo("José O'Brien-Silva");
    }

    @Test
    void shouldGenerateUniqueTokensForDifferentInvitations() throws Exception {
        // Given
        InviteUserRequest request1 = InviteUserRequest.builder()
                .email("unique1@example.com")
                .nome("User 1")
                .papeis(new String[]{"OPERADOR"})
                .build();

        InviteUserRequest request2 = InviteUserRequest.builder()
                .email("unique2@example.com")
                .nome("User 2")
                .papeis(new String[]{"GERENTE"})
                .build();

        // When - create two invitations
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1))
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                        .jwt(jwt -> jwt
                                .claim("tenant_id", TEST_TENANT_ID.toString())
                                .claim("roles", List.of("ADMIN_TENANT"))
                                .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2))
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                        .jwt(jwt -> jwt
                                .claim("tenant_id", TEST_TENANT_ID.toString())
                                .claim("roles", List.of("ADMIN_TENANT"))
                                .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isOk());

        // Then - tokens should be different
        Convite conv1 = conviteRepository.findByTenantIdAndEmail(TEST_TENANT_ID, "unique1@example.com").get();
        Convite conv2 = conviteRepository.findByTenantIdAndEmail(TEST_TENANT_ID, "unique2@example.com").get();

        assertThat(conv1.getToken()).isNotEqualTo(conv2.getToken());
    }

    @Test
    void shouldVerifyEmailServiceIntegration() throws Exception {
        // Given
        InviteUserRequest request = InviteUserRequest.builder()
                .email("email.integration@example.com")
                .nome("Email Test User")
                .papeis(new String[]{"FINANCEIRO"})
                .build();

        // When
        mockMvc.perform(post("/v1/tenants/{tenantId}/users/invite", TEST_TENANT_ID)
                        .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TEST_TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(ADMIN_USER_ID.toString()))))
                .andExpect(status().isOk());

        // Then - verify email service was called with correct parameters
        verify(emailService, times(1)).sendInvitationEmail(
                eq("email.integration@example.com"),
                eq("Email Test User"),
                argThat(loginUrl -> loginUrl.contains("token=")),
                anyString()  // Temporary password (generated randomly)
        );
    }
}
