package com.jetski.shared.authorization;

import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.security.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
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
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("ABACAuthorizationInterceptor Integration Tests")
class ABACAuthorizationInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    private static final String TENANT_ID = "123e4567-e89b-12d3-a456-426614174000";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(UUID.fromString(TENANT_ID));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("Request Interception")
    class RequestInterception {

        @Test
        @WithMockUser(username = "operador@test.com", roles = {"OPERADOR"})
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
        @DisplayName("Should bypass unauthenticated requests (handled by Spring Security)")
        void shouldBypassUnauthenticatedRequests() throws Exception {
            // When & Then - Spring Security should return 401 before ABAC interceptor
            mockMvc.perform(get("/v1/auth-test/me"))
                .andExpect(status().isUnauthorized());

            // OPA should NOT be called for unauthenticated requests
            verify(opaAuthorizationService, never()).authorize(any(OPAInput.class));
        }
    }

    @Nested
    @DisplayName("OPA Decision Handling")
    class OPADecisionHandling {

        @Test
        @WithMockUser(username = "operador@test.com", roles = {"OPERADOR"})
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
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "operador@test.com", roles = {"OPERADOR"})
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
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "operador@test.com", roles = {"OPERADOR"})
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

            // When & Then
            mockMvc.perform(get("/v1/auth-test/manager-only")
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isForbidden())
                .andExpect(status().reason(org.hamcrest.Matchers.containsString("GERENTE")));
        }

        @Test
        @WithMockUser(username = "operador@test.com", roles = {"OPERADOR"})
        @DisplayName("Should deny when tenant_is_valid=false")
        void shouldDenyWhenTenantInvalid() throws Exception {
            // Given
            OPADecision invalidTenantDecision = OPADecision.builder()
                .allow(false)
                .tenantIsValid(false)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(invalidTenantDecision);

            // When & Then
            mockMvc.perform(get("/v1/auth-test/me")
                    .header("X-Tenant-Id", "different-tenant-id"))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("OPAInput Building")
    class OPAInputBuilding {

        @Test
        @WithMockUser(username = "operador@test.com", roles = {"OPERADOR"})
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
        @WithMockUser(username = "gerente@test.com", roles = {"GERENTE"})
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
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk());

            // Then - verify user context
            verify(opaAuthorizationService).authorize(argThat(input -> {
                assertThat(input.getUser()).isNotNull();
                assertThat(input.getUser().getId()).isEqualTo("gerente@test.com");
                assertThat(input.getUser().getTenant_id()).isEqualTo(TENANT_ID);
                assertThat(input.getUser().getRole()).isEqualTo("GERENTE");
                return true;
            }));
        }

        @Test
        @WithMockUser(username = "operador@test.com", roles = {"OPERADOR"})
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
            mockMvc.perform(get("/v1/locacoes/" + locacaoId)
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
        @WithMockUser(username = "operador@test.com", roles = {"OPERADOR"})
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
        @WithMockUser(username = "operador@test.com", roles = {"OPERADOR"})
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
            mockMvc.perform(get("/v1/locacoes")
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isNotFound()); // 404 porque endpoint não existe

            // Then
            verify(opaAuthorizationService).authorize(argThat(input ->
                input.getAction().equals("locacao:list")
            ));
        }

        @Test
        @WithMockUser(username = "operador@test.com", roles = {"OPERADOR"})
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
            mockMvc.perform(post("/v1/locacoes/" + locacaoId + "/checkin")
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
        @WithMockUser(username = "gerente@test.com", roles = {"GERENTE"})
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
            mockMvc.perform(post("/v1/locacoes/" + locacaoId + "/desconto")
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
        @WithMockUser(username = "operador@test.com", roles = {"OPERADOR"})
        @DisplayName("Should validate tenant_id matches between user and resource")
        void shouldValidateTenantId() throws Exception {
            // Given - OPA should deny because tenant_is_valid=false
            OPADecision denyDecision = OPADecision.builder()
                .allow(false)
                .tenantIsValid(false)
                .build();

            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(denyDecision);

            String differentTenantId = UUID.randomUUID().toString();

            // When & Then
            mockMvc.perform(get("/v1/auth-test/me")
                    .header("X-Tenant-Id", differentTenantId))
                .andExpect(status().isForbidden());

            // Verify OPA was called with mismatched tenant_ids
            verify(opaAuthorizationService).authorize(argThat(input -> {
                // User tenant_id should be from TenantContext (TENANT_ID)
                // Resource tenant_id should be from header (differentTenantId)
                assertThat(input.getUser().getTenant_id()).isEqualTo(TENANT_ID);
                // Resource tenant_id might be different based on implementation
                return true;
            }));
        }

        @Test
        @WithMockUser(username = "admin@platform.com", roles = {"PLATFORM_ADMIN"})
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
                    .header("X-Tenant-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @WithMockUser(username = "operador@test.com", roles = {"OPERADOR"})
        @DisplayName("Should handle OPA service timeout gracefully")
        void shouldHandleOPATimeout() throws Exception {
            // Given
            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenThrow(new RuntimeException("OPA timeout"));

            // When & Then
            mockMvc.perform(get("/v1/auth-test/me")
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().is5xxServerError());
        }

        @Test
        @WithMockUser(username = "operador@test.com", roles = {"OPERADOR"})
        @DisplayName("Should deny when OPA returns null decision")
        void shouldDenyWhenOPAReturnsNull() throws Exception {
            // Given
            when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(null);

            // When & Then - should be treated as deny
            mockMvc.perform(get("/v1/auth-test/me")
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Real-World Scenarios")
    class RealWorldScenarios {

        @Test
        @WithMockUser(username = "operador@test.com", roles = {"OPERADOR"})
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
            mockMvc.perform(get("/v1/locacoes")
                    .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isNotFound()); // 404 porque endpoint não existe, mas passou ABAC

            verify(opaAuthorizationService).authorize(argThat(input ->
                input.getAction().equals("locacao:list") &&
                input.getUser().getRole().equals("OPERADOR")
            ));
        }

        @Test
        @WithMockUser(username = "operador@test.com", roles = {"OPERADOR"})
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
            mockMvc.perform(post("/v1/locacoes/" + locacaoId + "/desconto")
                    .header("X-Tenant-Id", TENANT_ID)
                    .contentType("application/json")
                    .content("{\"percentual\": 15}"))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "gerente@test.com", roles = {"GERENTE"})
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
            mockMvc.perform(post("/v1/locacoes/" + locacaoId + "/desconto")
                    .header("X-Tenant-Id", TENANT_ID)
                    .contentType("application/json")
                    .content("{\"percentual\": 15}"))
                .andExpect(status().isNotFound()); // Passed ABAC, endpoint not implemented
        }

        @Test
        @WithMockUser(username = "admin@tenant.com", roles = {"ADMIN_TENANT"})
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
            mockMvc.perform(post("/v1/fechamentos/mensal")
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
