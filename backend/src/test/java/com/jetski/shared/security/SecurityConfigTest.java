package com.jetski.shared.security;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.usuarios.internal.TenantAccessService;
import com.jetski.shared.security.TenantAccessInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for SecurityConfig
 *
 * Tests security filter chains, CORS configuration, and authorization rules.
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@AutoConfigureMockMvc
@DisplayName("SecurityConfig Tests")
class SecurityConfigTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @MockBean
    private TenantAccessService tenantAccessService;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    @BeforeEach
    void setUp() {
        // Mock OPA to allow all requests by default
        OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();
        when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);
    }

    // ========================================================================
    // Public Filter Chain Tests (@Order(1))
    // ========================================================================

    @Test
    @DisplayName("Should allow access to /actuator/health without authentication")
    void shouldAllowHealthEndpointWithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    // Note: /actuator/info is not exposed by default in Spring Boot

    @Test
    @DisplayName("Should allow access to /swagger-ui.html without authentication")
    void shouldAllowSwaggerHtmlWithoutAuth() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection()); // Redirects to /swagger-ui/index.html
    }

    @Test
    @DisplayName("Should allow access to /swagger-ui/** without authentication")
    void shouldAllowSwaggerUiWithoutAuth() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should allow access to /v3/api-docs without authentication")
    void shouldAllowApiDocsWithoutAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"));
    }

    @Test
    @DisplayName("Should allow access to /v1/auth-test/public without authentication")
    void shouldAllowPublicAuthTestEndpointWithoutAuth() throws Exception {
        mockMvc.perform(get("/v1/auth-test/public"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Public endpoint - no authentication required"));
    }

    // ========================================================================
    // Protected Filter Chain Tests (@Order(2))
    // ========================================================================

    @Test
    @DisplayName("Should require authentication for protected endpoints")
    void shouldRequireAuthenticationForProtectedEndpoints() throws Exception {
        // User tenants endpoint requires authentication
        mockMvc.perform(get("/v1/user/tenants"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should allow authenticated request to protected endpoint")
    void shouldAllowAuthenticatedRequestToProtectedEndpoint() throws Exception {
        UUID usuarioId = UUID.randomUUID();

        mockMvc.perform(get("/v1/user/tenants")
                .with(jwt().jwt(jwt -> jwt.subject(usuarioId.toString()))))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should enforce role-based access for /v1/auth-test/me endpoint")
    void shouldEnforceRoleBasedAccessForMeEndpoint() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

        // Mock tenant access validation
        TenantAccessInfo accessInfo = TenantAccessInfo.builder()
            .hasAccess(true)
            .roles(List.of("GERENTE"))
            .unrestricted(false)
            .build();
        when(tenantAccessService.validateAccess(usuarioId, tenantId)).thenReturn(accessInfo);

        // With valid authentication and tenant header, should work
        mockMvc.perform(get("/v1/auth-test/me")
                .header("X-Tenant-Id", tenantId.toString())
                .with(jwt().jwt(jwt -> jwt
                    .subject(usuarioId.toString())
                    .claim("tenant_id", tenantId.toString()))))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should reject request with invalid JWT")
    void shouldRejectInvalidJwt() throws Exception {
        mockMvc.perform(get("/v1/user/tenants")
                .header("Authorization", "Bearer invalid.jwt.token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject request without Bearer token")
    void shouldRejectWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/v1/user/tenants")
                .header("Authorization", "Basic dXNlcjpwYXNz")) // Basic auth instead of Bearer
            .andExpect(status().isUnauthorized());
    }

    // ========================================================================
    // CORS Configuration Tests
    // ========================================================================

    @Test
    @DisplayName("CORS configuration should be defined")
    void corsConfigurationShouldBeDefined() {
        assertThat(corsConfigurationSource).isNotNull();
    }

    @Test
    @DisplayName("CORS configuration should allow configured origins")
    void corsConfigurationShouldAllowConfiguredOrigins() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/user/tenants");

        var corsConfig = corsConfigurationSource.getCorsConfiguration(request);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowedOriginPatterns())
            .containsExactlyInAnyOrder(
                "http://localhost:3000",
                "http://localhost:3001",
                "https://*.jetski.app",
                "https://*.jetski.com.br"
            );
    }

    @Test
    @DisplayName("CORS configuration should allow standard HTTP methods")
    void corsConfigurationShouldAllowStandardMethods() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/user/tenants");

        var corsConfig = corsConfigurationSource.getCorsConfiguration(request);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowedMethods())
            .containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }

    @Test
    @DisplayName("CORS configuration should allow required headers")
    void corsConfigurationShouldAllowRequiredHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/user/tenants");

        var corsConfig = corsConfigurationSource.getCorsConfiguration(request);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowedHeaders())
            .containsExactlyInAnyOrder("Authorization", "Content-Type", "X-Tenant-Id", "X-Request-Id");
    }

    @Test
    @DisplayName("CORS configuration should expose response headers")
    void corsConfigurationShouldExposeResponseHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/user/tenants");

        var corsConfig = corsConfigurationSource.getCorsConfiguration(request);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getExposedHeaders())
            .containsExactlyInAnyOrder("X-Total-Count", "X-Request-Id");
    }

    @Test
    @DisplayName("CORS configuration should allow credentials")
    void corsConfigurationShouldAllowCredentials() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/user/tenants");

        var corsConfig = corsConfigurationSource.getCorsConfiguration(request);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowCredentials()).isTrue();
    }

    @Test
    @DisplayName("CORS configuration should set max age for preflight cache")
    void corsConfigurationShouldSetMaxAge() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/user/tenants");

        var corsConfig = corsConfigurationSource.getCorsConfiguration(request);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getMaxAge()).isEqualTo(3600L);
    }

    // ========================================================================
    // CORS Preflight (OPTIONS) Tests
    // ========================================================================

    @Test
    @DisplayName("Should handle CORS preflight request from localhost:3000")
    void shouldHandleCorsPreflight() throws Exception {
        mockMvc.perform(options("/v1/user/tenants")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization, X-Tenant-Id"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Access-Control-Allow-Origin"))
            .andExpect(header().exists("Access-Control-Allow-Methods"))
            .andExpect(header().exists("Access-Control-Allow-Headers"));
    }

    @Test
    @DisplayName("Should handle CORS preflight for POST request")
    void shouldHandleCorsPreflightPost() throws Exception {
        mockMvc.perform(options("/v1/auth-test/public")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    // ========================================================================
    // Session Management Tests
    // ========================================================================

    @Test
    @DisplayName("Should not create sessions (stateless)")
    void shouldNotCreateSessions() throws Exception {
        // Public endpoint should not create session
        var result = mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    @DisplayName("Should not create sessions for authenticated requests")
    void shouldNotCreateSessionsForAuthenticatedRequests() throws Exception {
        UUID usuarioId = UUID.randomUUID();

        var result = mockMvc.perform(get("/v1/user/tenants")
                .with(jwt().jwt(jwt -> jwt.subject(usuarioId.toString()))))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    // ========================================================================
    // Filter Order Tests
    // ========================================================================

    @Test
    @DisplayName("Public filter chain should process public endpoints first")
    void publicFilterChainShouldProcessPublicEndpointsFirst() throws Exception {
        // Public endpoint should be accessible without any authentication setup
        // If protected chain ran first, it would require JWT validation
        mockMvc.perform(get("/v1/auth-test/public"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Protected filter chain should process non-public endpoints")
    void protectedFilterChainShouldProcessNonPublicEndpoints() throws Exception {
        // Non-public endpoint should be processed by protected chain
        // which requires authentication
        mockMvc.perform(get("/v1/user/tenants"))
            .andExpect(status().isUnauthorized());
    }

    // ========================================================================
    // TenantFilter Integration Tests
    // ========================================================================

    @Test
    @DisplayName("Should validate tenant header for protected endpoints")
    void shouldValidateTenantHeaderForProtectedEndpoints() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // Without X-Tenant-Id header, should fail tenant validation
        mockMvc.perform(get("/v1/auth-test/me")
                .with(jwt().jwt(jwt -> jwt
                    .subject(usuarioId.toString())
                    .claim("tenant_id", tenantId.toString()))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should accept valid tenant header matching JWT claim")
    void shouldAcceptValidTenantHeader() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // Mock tenant access validation
        TenantAccessInfo accessInfo = TenantAccessInfo.builder()
            .hasAccess(true)
            .roles(List.of("OPERADOR"))
            .unrestricted(false)
            .build();
        when(tenantAccessService.validateAccess(usuarioId, tenantId)).thenReturn(accessInfo);

        mockMvc.perform(get("/v1/auth-test/me")
                .header("X-Tenant-Id", tenantId.toString())
                .with(jwt().jwt(jwt -> jwt
                    .subject(usuarioId.toString())
                    .claim("tenant_id", tenantId.toString()))))
            .andExpect(status().isOk());
    }
}
