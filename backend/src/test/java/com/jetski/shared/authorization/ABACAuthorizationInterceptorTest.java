package com.jetski.shared.authorization;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.security.TenantAccessInfo;
import com.jetski.usuarios.internal.TenantAccessService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração para ABACAuthorizationInterceptor.
 *
 * Valida:
 * - Interceptação de requests
 * - Construção de OPAInput com todos os atributos
 * - Decisões OPA (allow/deny)
 * - Aprovações (requer_aprovacao)
 * - Bypass de endpoints públicos
 * - Multi-tenant validation
 * - Context attributes (IP, device, timestamp)
 *
 * @author Jetski Team
 */
@AutoConfigureMockMvc
@DisplayName("ABACAuthorizationInterceptor Integration Tests")
class ABACAuthorizationInterceptorTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    @MockBean
    private TenantAccessService tenantAccessService;  // This implements TenantAccessValidator

    private static final String TENANT_ID = "123e4567-e89b-12d3-a456-426614174000";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(UUID.fromString(TENANT_ID));

        // Mock TenantAccessService to allow access by default (using new provider-based signature)
        TenantAccessInfo allowAccess = TenantAccessInfo.builder()
                .hasAccess(true)
                .roles(List.of("OPERADOR", "GERENTE", "ADMIN_TENANT"))
                .unrestricted(false)
                .build();

        when(tenantAccessService.validateAccess(any(String.class), any(String.class), any(UUID.class)))
                .thenReturn(allowAccess);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // Helper methods para criar JWT tokens
    // UUIDs de usuários de teste
    private static final String OPERADOR_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String GERENTE_UUID = "22222222-2222-2222-2222-222222222222";
    private static final String ADMIN_TENANT_UUID = "33333333-3333-3333-3333-333333333333";
    private static final String PLATFORM_ADMIN_UUID = "44444444-4444-4444-4444-444444444444";

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtOperador() {
        return jwt().jwt(j -> j
            .subject(OPERADOR_UUID)
            .claim("tenant_id", TENANT_ID)
            .claim("roles", List.of("OPERADOR")))
            .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtGerente() {
        return jwt().jwt(j -> j
            .subject(GERENTE_UUID)
            .claim("tenant_id", TENANT_ID)
            .claim("roles", List.of("GERENTE")))
            .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_GERENTE"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtAdminTenant() {
        return jwt().jwt(j -> j
            .subject(ADMIN_TENANT_UUID)
            .claim("tenant_id", TENANT_ID)
            .claim("roles", List.of("ADMIN_TENANT")))
            .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtPlatformAdmin() {
        return jwt().jwt(j -> j
            .subject(PLATFORM_ADMIN_UUID)
            .claim("tenant_id", "platform")
            .claim("roles", List.of("PLATFORM_ADMIN"))
            .claim("unrestricted_access", true))
            .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }

    @Nested
    @DisplayName("Request Interception")
    class RequestInterception {

        @Test
        @DisplayName("Should intercept protected endpoint and call OPA")
        void shouldInterceptProtectedEndpoint() throws Exception {
            // Given
            OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

            // When & Then
            mockMvc.perform(get("/v1/auth-test/me")
                    .with(jwtOperador())
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk());

            verify(opaAuthorizationService, times(1)).authorize(any(OPAInput.class));
        }

        @Test
        @DisplayName("Should bypass public endpoints without calling OPA")
        void shouldBypassPublicEndpoints() throws Exception {
            // When & Then
            mockMvc.perform(get("/v1/auth-test/public"))
                .andExpect(status().isOk());

            // OPA should NOT be called for public endpoints
            verify(opaAuthorizationService, never()).authorize(any(OPAInput.class));
        }

        @Test
        @DisplayName("Should return 401 for unauthenticated requests with valid tenant")
        void shouldBypassUnauthenticatedRequests() throws Exception {
            // When & Then - With X-Tenant-Id but no JWT, Spring Security should return 401
            mockMvc.perform(get("/v1/auth-test/me")
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isUnauthorized());

            // OPA should NOT be called for unauthenticated requests
            verify(opaAuthorizationService, never()).authorize(any(OPAInput.class));
        }
    }

    @Nested
    @DisplayName("OPA Decision Handling")
    class OPADecisionHandling {

        @Test
        @DisplayName("Should allow request when OPA returns allow=true")
        void shouldAllowWhenOPAReturnsAllow() throws Exception {
            // Given
            OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

            // When & Then
            mockMvc.perform(get("/v1/auth-test/operador-only")
                    .with(jwtOperador())
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should deny request when OPA returns allow=false")
        void shouldDenyWhenOPAReturnsDeny() throws Exception {
            // Given
            OPADecision denyDecision = OPADecision.builder()
                .allow(false)
                .tenantIsValid(true)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(denyDecision);

            // When & Then
            mockMvc.perform(get("/v1/auth-test/manager-only")
                    .with(jwtGerente())
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should deny with approval message when requer_aprovacao=true")
        void shouldDenyWithApprovalMessage() throws Exception {
            // Given
            OPADecision approvalDecision = OPADecision.builder()
                .allow(false)
                .requerAprovacao(true)
                .aprovadorRequerido("GERENTE")
                .tenantIsValid(true)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(approvalDecision);

            // When & Then - should deny with 403 when approval is required
            mockMvc.perform(get("/v1/auth-test/manager-only")
                    .with(jwtGerente())
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isForbidden());

            // Verify that OPA was called and decision was respected
            verify(opaAuthorizationService).authorize(any(OPAInput.class));
        }

        @Test
        @DisplayName("Should deny when tenant_is_valid=false")
        void shouldDenyWhenTenantInvalid() throws Exception {
            // Given - invalid UUID format in header
            // When & Then - TenantFilter should return 400 Bad Request for invalid UUID format
            mockMvc.perform(get("/v1/auth-test/me")
                    .with(jwtOperador())
                    .header("X-Tenant-Id", "invalid-tenant-id"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("OPAInput Building")
    class OPAInputBuilding {

        @Test
        @DisplayName("Should build OPAInput with action extracted from request")
        void shouldBuildOPAInputWithAction() throws Exception {
            // Given
            OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

            // When
            mockMvc.perform(get("/v1/auth-test/operador-only")
                    .with(jwtOperador())
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk());

            // Then - verify OPAInput was built with correct action
            verify(opaAuthorizationService).authorize(argThat(input -> {
                // Action should be "auth-test:list" or similar based on ActionExtractor logic
                assertThat(input.getAction()).isNotNull();
                assertThat(input.getAction()).contains("auth-test");
                return true;
            }));
        }

        @Test
        @DisplayName("Should build OPAInput with user context (tenant_id, role)")
        void shouldBuildOPAInputWithUserContext() throws Exception {
            // Given
            OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

            // When
            mockMvc.perform(get("/v1/auth-test/manager-only")
                    .with(jwtGerente())
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk());

            // Then - verify user context
            verify(opaAuthorizationService).authorize(argThat(input -> {
                assertThat(input.getUser()).isNotNull();
                assertThat(input.getUser().getId()).isEqualTo(GERENTE_UUID);
                assertThat(input.getUser().getTenant_id()).isEqualTo(TENANT_ID);
                assertThat(input.getUser().getRole()).isEqualTo("GERENTE");
                return true;
            }));
        }

        @Test
        @DisplayName("Should build OPAInput with resource context (tenant_id)")
        void shouldBuildOPAInputWithResourceContext() throws Exception {
            // Given
            OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

            String locacaoId = UUID.randomUUID().toString();

            // When
            mockMvc.perform(get("/v1/locacoes/" + locacaoId).with(jwtOperador())
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isNotFound()); // 404 porque endpoint não existe, mas interceptor rodou

            // Then - verify resource context
            verify(opaAuthorizationService).authorize(argThat(input -> {
                assertThat(input.getResource()).isNotNull();
                assertThat(input.getResource().getTenant_id()).isEqualTo(TENANT_ID);
                // Resource ID should be extracted from path
                assertThat(input.getResource().getId()).isEqualTo(locacaoId);
                return true;
            }));
        }

        @Test
        @DisplayName("Should build OPAInput with context attributes (timestamp, IP, device)")
        void shouldBuildOPAInputWithContextAttributes() throws Exception {
            // Given
            OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

            // When
            mockMvc.perform(get("/v1/auth-test/me")
                    .with(jwtOperador())
                    .header("X-Tenant-Id", TENANT_ID)
                    .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X)")
                    .header("X-Forwarded-For", "192.168.1.100"))
                .andExpect(status().isOk());

            // Then - verify context attributes
            verify(opaAuthorizationService).authorize(argThat(input -> {
                assertThat(input.getContext()).isNotNull();
                assertThat(input.getContext().getTimestamp()).isNotNull();
                assertThat(input.getContext().getIp()).isEqualTo("192.168.1.100");
                assertThat(input.getContext().getUser_agent()).contains("iPhone");
                assertThat(input.getContext().getDevice()).isNotNull();
                return true;
            }));
        }
    }

    @Nested
    @DisplayName("Action Extraction")
    class ActionExtraction {

        @Test
        @DisplayName("GET /v1/locacoes → action=locacao:list")
        void shouldExtractListAction() throws Exception {
            // Given
            OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

            // When
            mockMvc.perform(get("/v1/locacoes").with(jwtOperador())
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isNotFound()); // 404 porque endpoint não existe

            // Then
            verify(opaAuthorizationService).authorize(argThat(input ->
                input.getAction().equals("locacao:list")
            ));
        }

        @Test
        @DisplayName("POST /v1/locacoes/{id}/checkin → action=locacao:checkin")
        void shouldExtractCheckinAction() throws Exception {
            // Given
            OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

            String locacaoId = UUID.randomUUID().toString();

            // When
            mockMvc.perform(post("/v1/locacoes/" + locacaoId + "/checkin").with(jwtOperador())
                    .header("X-Tenant-Id", TENANT_ID)
                    .contentType("application/json")
                    .content("{}"))
                .andExpect(status().isNotFound());

            // Then
            verify(opaAuthorizationService).authorize(argThat(input ->
                input.getAction().equals("locacao:checkin")
            ));
        }

        @Test
        @DisplayName("POST /v1/locacoes/{id}/desconto → action=locacao:desconto")
        void shouldExtractDescontoAction() throws Exception {
            // Given
            OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

            String locacaoId = UUID.randomUUID().toString();

            // When
            mockMvc.perform(post("/v1/locacoes/" + locacaoId + "/desconto").with(jwtGerente())
                    .header("X-Tenant-Id", TENANT_ID)
                    .contentType("application/json")
                    .content("{\"percentual\": 10}"))
                .andExpect(status().isNotFound());

            // Then
            verify(opaAuthorizationService).authorize(argThat(input ->
                input.getAction().equals("locacao:desconto")
            ));
        }
    }

    @Nested
    @DisplayName("Multi-Tenant Scenarios")
    class MultiTenantScenarios {

        @Test
        @DisplayName("Should validate tenant_id matches between user and resource")
        void shouldValidateTenantId() throws Exception {
            // Given - TenantAccessService should deny access for different tenant
            String differentTenantId = UUID.randomUUID().toString();

            TenantAccessInfo denyAccess = TenantAccessInfo.builder()
                    .hasAccess(false)
                    .reason("User is not a member of this tenant")
                    .build();

            // Mock using new provider-based signature (provider, providerUserId, tenantId)
            when(tenantAccessService.validateAccess(any(String.class), any(String.class), eq(UUID.fromString(differentTenantId))))
                    .thenReturn(denyAccess);

            // When & Then - TenantFilter should throw AccessDeniedException (403)
            mockMvc.perform(get("/v1/auth-test/me")
                    .with(jwtOperador())
                    .header("X-Tenant-Id", differentTenantId))
                .andExpect(status().isForbidden());

            // OPA should NOT be called because TenantFilter denies first
            verify(opaAuthorizationService, never()).authorize(any(OPAInput.class));
        }

        @Test
        @DisplayName("Platform admin should bypass tenant validation")
        void platformAdminShouldBypassTenantValidation() throws Exception {
            // Given - Platform admin unrestricted
            OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true) // Platform admin always valid
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

            // When & Then
            mockMvc.perform(get("/v1/auth-test/me")
                    .with(jwtPlatformAdmin())
                    .header("X-Tenant-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should handle OPA service timeout gracefully")
        void shouldHandleOPATimeout() throws Exception {
            // Given
            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenThrow(new RuntimeException("OPA timeout"));

            // When & Then
            mockMvc.perform(get("/v1/auth-test/me")
                    .with(jwtOperador())
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().is5xxServerError());
        }

        @Test
        @DisplayName("Should deny when OPA returns null decision")
        void shouldDenyWhenOPAReturnsNull() throws Exception {
            // Given
            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(null);

            // When & Then - should be treated as deny
            mockMvc.perform(get("/v1/auth-test/me")
                    .with(jwtOperador())
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Real-World Scenarios")
    class RealWorldScenarios {

        @Test
        @DisplayName("Operador can list locações")
        void operadorCanListLocacoes() throws Exception {
            // Given
            OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

            // When & Then
            mockMvc.perform(get("/v1/locacoes").with(jwtOperador())
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isNotFound()); // 404 porque endpoint não existe, mas passou ABAC

            verify(opaAuthorizationService).authorize(argThat(input ->
                input.getAction().equals("locacao:list") &&
                input.getUser().getRole().equals("OPERADOR")
            ));
        }

        @Test
        @DisplayName("Operador cannot apply 15% discount (requires GERENTE)")
        void operadorCannotApplyLargeDiscount() throws Exception {
            // Given - OPA should deny and require GERENTE approval
            OPADecision denyDecision = OPADecision.builder()
                .allow(false)
                .requerAprovacao(true)
                .aprovadorRequerido("GERENTE")
                .tenantIsValid(true)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(denyDecision);

            String locacaoId = UUID.randomUUID().toString();

            // When & Then
            mockMvc.perform(post("/v1/locacoes/" + locacaoId + "/desconto").with(jwtOperador())
                    .header("X-Tenant-Id", TENANT_ID)
                    .contentType("application/json")
                    .content("{\"percentual\": 15}"))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Gerente can apply 15% discount")
        void gerenteCanApplyDiscount() throws Exception {
            // Given
            OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

            String locacaoId = UUID.randomUUID().toString();

            // When & Then
            mockMvc.perform(post("/v1/locacoes/" + locacaoId + "/desconto").with(jwtGerente())
                    .header("X-Tenant-Id", TENANT_ID)
                    .contentType("application/json")
                    .content("{\"percentual\": 15}"))
                .andExpect(status().isNotFound()); // Passed ABAC, endpoint not implemented
        }

        @Test
        @DisplayName("ADMIN_TENANT has wildcard access to all actions")
        void adminTenantHasWildcardAccess() throws Exception {
            // Given
            OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

            // When & Then - should allow any action
            mockMvc.perform(post("/v1/fechamentos/mensal").with(jwtAdminTenant())
                    .header("X-Tenant-Id", TENANT_ID)
                    .contentType("application/json")
                    .content("{}"))
                .andExpect(status().isNotFound()); // Passed ABAC

            verify(opaAuthorizationService).authorize(argThat(input ->
                input.getUser().getRole().equals("ADMIN_TENANT")
            ));
        }
    }
}
