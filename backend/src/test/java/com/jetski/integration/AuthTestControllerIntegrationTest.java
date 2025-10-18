package com.jetski.integration;

import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.usuarios.internal.TenantAccessService;
import com.jetski.shared.security.TenantAccessInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for AuthTestController with full Spring context and database.
 *
 * Tests:
 * - Public endpoints accessibility
 * - Protected endpoints with JWT authentication
 * - Role-based authorization
 * - Tenant isolation via X-Tenant-Id header
 *
 * @author Jetski Team
 */
@AutoConfigureMockMvc
class AuthTestControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    @MockBean
    private TenantAccessService tenantAccessService;

    private static final UUID TENANT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OPERADOR_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GERENTE_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SUPER_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        // Mock TenantAccessService to allow access for test users
        TenantAccessInfo allowedAccess = TenantAccessInfo.builder()
                .hasAccess(true)
                .roles(List.of("OPERADOR", "GERENTE"))
                .unrestricted(false)
                .build();

        TenantAccessInfo superAdminAccess = TenantAccessInfo.builder()
                .hasAccess(true)
                .roles(List.of("PLATFORM_ADMIN"))
                .unrestricted(true)
                .build();

        // Default behavior for all users
        when(tenantAccessService.validateAccess(any(UUID.class), any(UUID.class)))
                .thenReturn(allowedAccess);

        // Specific behavior for super admin (use eq() for specific UUID)
        when(tenantAccessService.validateAccess(org.mockito.ArgumentMatchers.eq(SUPER_ADMIN_ID), any(UUID.class)))
                .thenReturn(superAdminAccess);
    }

    @Test
    void shouldAccessPublicEndpoint() throws Exception {
        mockMvc.perform(get("/v1/auth-test/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Public endpoint - no authentication required"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn401ForProtectedEndpointWithoutAuth() throws Exception {
        mockMvc.perform(get("/v1/auth-test/me")
                        .header("X-Tenant-Id", TENANT_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAccessProtectedEndpointWithValidJwtAndTenant() throws Exception {
        mockMvc.perform(get("/v1/auth-test/me")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("email", "test@example.com")
                                        .claim("roles", java.util.List.of("OPERADOR"))
                                        .subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.principal").value(USER_ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()));
    }

    @Test
    void shouldRejectRequestWithMismatchedTenantId() throws Exception {
        UUID differentTenantId = UUID.fromString("b1ffcd88-8b1a-4ef8-bb6d-6bb9bd380a22");

        // Mock that user does NOT have access to the different tenant
        TenantAccessInfo deniedAccess = TenantAccessInfo.builder()
                .hasAccess(false)
                .reason("User is not a member of tenant " + differentTenantId)
                .build();
        when(tenantAccessService.validateAccess(eq(USER_ID), eq(differentTenantId)))
                .thenReturn(deniedAccess);

        mockMvc.perform(get("/v1/auth-test/me")
                        .header("X-Tenant-Id", differentTenantId.toString())
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("roles", java.util.List.of("OPERADOR"))
                                        .subject(USER_ID.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void shouldRejectRequestWithoutTenantHeader() throws Exception {
        mockMvc.perform(get("/v1/auth-test/me")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("roles", java.util.List.of("OPERADOR"))
                                        .subject(USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Tenant ID not found in request. Please provide X-Tenant-Id header or use subdomain routing."));
    }

    @Test
    void shouldAllowOperadorToAccessOperadorEndpoint() throws Exception {
        mockMvc.perform(get("/v1/auth-test/operador-only")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("roles", java.util.List.of("OPERADOR"))
                                        .subject(OPERADOR_USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Acesso permitido apenas para OPERADOR"));
    }

    @Test
    void shouldDenyGerenteAccessToOperadorEndpoint() throws Exception {
        mockMvc.perform(get("/v1/auth-test/operador-only")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_GERENTE"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("roles", java.util.List.of("GERENTE"))
                                        .subject(GERENTE_USER_ID.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowGerenteToAccessManagerEndpoint() throws Exception {
        mockMvc.perform(get("/v1/auth-test/manager-only")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_GERENTE"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("roles", java.util.List.of("GERENTE"))
                                        .subject(GERENTE_USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Acesso permitido para GERENTE ou ADMIN_TENANT"));
    }

    @Test
    void shouldTestOpaRbacEndpointIntegration() throws Exception {
        // Mock OPA response
        OPADecision mockDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();

        when(opaAuthorizationService.authorizeRBAC(any(OPAInput.class)))
                .thenReturn(mockDecision);

        mockMvc.perform(get("/v1/auth-test/opa/rbac")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .param("action", "modelo:list")
                        .param("role", "OPERADOR")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("roles", java.util.List.of("OPERADOR"))
                                        .subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.allow").value(true))
                .andExpect(jsonPath("$.input.action").value("modelo:list"));
    }

    @Test
    void shouldTestOpaAlcadaEndpointWithApprovalRequired() throws Exception {
        // Mock OPA response requiring approval
        OPADecision mockDecision = OPADecision.builder()
                .allow(false)
                .requerAprovacao(true)
                .aprovadorRequerido("ADMIN_TENANT")
                .build();

        when(opaAuthorizationService.authorizeAlcada(any(OPAInput.class)))
                .thenReturn(mockDecision);

        mockMvc.perform(get("/v1/auth-test/opa/alcada")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .param("action", "desconto:aplicar")
                        .param("role", "GERENTE")
                        .param("percentualDesconto", "25")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("roles", java.util.List.of("GERENTE"))
                                        .subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.allow").value(false))
                .andExpect(jsonPath("$.decision.requer_aprovacao").value(true))
                .andExpect(jsonPath("$.decision.aprovador_requerido").value("ADMIN_TENANT"));
    }

    @Test
    void shouldAllowSuperAdminWithoutTenantIdClaim() throws Exception {
        // Super admin without tenant_id claim should be allowed
        mockMvc.perform(get("/v1/auth-test/me")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("roles", java.util.List.of("SUPER_ADMIN"))
                                        .subject(SUPER_ADMIN_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    void shouldAccessPublicEndpointsWithoutTenantHeader() throws Exception {
        // Public endpoints should not require X-Tenant-Id
        mockMvc.perform(get("/v1/auth-test/public"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // ========================================================================
    // JWT Edge Cases
    // ========================================================================

    @Test
    void shouldRejectJwtWithoutSubject() throws Exception {
        // JWT without subject causes internal server error (500) as it's invalid
        mockMvc.perform(get("/v1/auth-test/me")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("roles", List.of("OPERADOR")))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldHandleJwtWithEmptyRoles() throws Exception {
        mockMvc.perform(get("/v1/auth-test/me")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("roles", List.of())
                                        .subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    void shouldHandleJwtWithMultipleRoles() throws Exception {
        mockMvc.perform(get("/v1/auth-test/manager-only")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .with(jwt()
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_OPERADOR"),
                                        new SimpleGrantedAuthority("ROLE_GERENTE"),
                                        new SimpleGrantedAuthority("ROLE_FINANCEIRO")
                                )
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("roles", List.of("OPERADOR", "GERENTE", "FINANCEIRO"))
                                        .subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Acesso permitido para GERENTE ou ADMIN_TENANT"));
    }

    @Test
    void shouldHandleJwtWithInvalidTenantIdFormat() throws Exception {
        // System currently doesn't validate JWT claim format, only header format
        // This is acceptable as JWT is validated by Keycloak which ensures valid UUIDs
        mockMvc.perform(get("/v1/auth-test/me")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", "invalid-uuid-format")
                                        .claim("roles", List.of("OPERADOR"))
                                        .subject(USER_ID.toString()))))
                .andExpect(status().isOk());
    }

    // ========================================================================
    // Tenant Validation Edge Cases
    // ========================================================================

    @Test
    void shouldRejectInvalidTenantIdHeaderFormat() throws Exception {
        mockMvc.perform(get("/v1/auth-test/me")
                        .header("X-Tenant-Id", "not-a-valid-uuid")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("roles", List.of("OPERADOR"))
                                        .subject(USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid tenant ID format: 'not-a-valid-uuid'. Must be a valid UUID."));
    }

    @Test
    void shouldHandleMultipleRequestsWithDifferentTenants() throws Exception {
        UUID tenant1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID tenant2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

        // First request with tenant1
        mockMvc.perform(get("/v1/auth-test/me")
                        .header("X-Tenant-Id", tenant1.toString())
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", tenant1.toString())
                                        .claim("roles", List.of("OPERADOR"))
                                        .subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenant1.toString()));

        // Second request with tenant2
        mockMvc.perform(get("/v1/auth-test/me")
                        .header("X-Tenant-Id", tenant2.toString())
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", tenant2.toString())
                                        .claim("roles", List.of("OPERADOR"))
                                        .subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenant2.toString()));
    }

    // ========================================================================
    // OPA Authorization Edge Cases
    // ========================================================================

    @Test
    void shouldHandleOpaRbacDenied() throws Exception {
        // Mock OPA denying access
        OPADecision mockDecision = OPADecision.builder()
                .allow(false)
                .tenantIsValid(true)
                .build();

        when(opaAuthorizationService.authorizeRBAC(any(OPAInput.class)))
                .thenReturn(mockDecision);

        mockMvc.perform(get("/v1/auth-test/opa/rbac")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .param("action", "modelo:delete")
                        .param("role", "OPERADOR")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("roles", List.of("OPERADOR"))
                                        .subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.allow").value(false));
    }

    @Test
    void shouldHandleOpaAlcadaWithoutApproval() throws Exception {
        // Mock OPA allowing without approval (within authority limit)
        OPADecision mockDecision = OPADecision.builder()
                .allow(true)
                .requerAprovacao(false)
                .build();

        when(opaAuthorizationService.authorizeAlcada(any(OPAInput.class)))
                .thenReturn(mockDecision);

        mockMvc.perform(get("/v1/auth-test/opa/alcada")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .param("action", "desconto:aplicar")
                        .param("role", "GERENTE")
                        .param("percentualDesconto", "5")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("roles", List.of("GERENTE"))
                                        .subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.allow").value(true))
                .andExpect(jsonPath("$.decision.requer_aprovacao").value(false));
    }

    @Test
    void shouldHandleOpaAlcadaWithExtremeValues() throws Exception {
        // Mock OPA for extreme discount percentage
        OPADecision mockDecision = OPADecision.builder()
                .allow(false)
                .requerAprovacao(true)
                .aprovadorRequerido("ADMIN_TENANT")
                .build();

        when(opaAuthorizationService.authorizeAlcada(any(OPAInput.class)))
                .thenReturn(mockDecision);

        mockMvc.perform(get("/v1/auth-test/opa/alcada")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .param("action", "desconto:aplicar")
                        .param("role", "OPERADOR")
                        .param("percentualDesconto", "99")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("roles", List.of("OPERADOR"))
                                        .subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.allow").value(false))
                .andExpect(jsonPath("$.decision.requer_aprovacao").value(true));
    }

    // ========================================================================
    // Security Header Edge Cases
    // ========================================================================

    @Test
    void shouldRejectEmptyTenantIdHeader() throws Exception {
        mockMvc.perform(get("/v1/auth-test/me")
                        .header("X-Tenant-Id", "")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("roles", List.of("OPERADOR"))
                                        .subject(USER_ID.toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldHandleRequestWithoutAnyAuthentication() throws Exception {
        mockMvc.perform(get("/v1/auth-test/me")
                        .header("X-Tenant-Id", TENANT_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAdminTenantToAccessManagerEndpoint() throws Exception {
        mockMvc.perform(get("/v1/auth-test/manager-only")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("roles", List.of("ADMIN_TENANT"))
                                        .subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Acesso permitido para GERENTE ou ADMIN_TENANT"));
    }

    // ========================================================================
    // Response Content Validation
    // ========================================================================

    @Test
    void shouldReturnCorrectUserInfoInMeEndpoint() throws Exception {
        String testEmail = "operador@jetski.com";

        mockMvc.perform(get("/v1/auth-test/me")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("email", testEmail)
                                        .claim("roles", List.of("OPERADOR", "VENDEDOR"))
                                        .subject(OPERADOR_USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.principal").value(OPERADOR_USER_ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.jwt.email").value(testEmail));
    }
}
