package com.jetski.integration;

import com.jetski.opa.dto.OPADecision;
import com.jetski.opa.dto.OPAInput;
import com.jetski.opa.service.OPAAuthorizationService;
import com.jetski.service.TenantAccessService;
import com.jetski.service.dto.TenantAccessInfo;
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
}
