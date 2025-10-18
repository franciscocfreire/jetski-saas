package com.jetski.shared.authorization;

import com.jetski.shared.authorization.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OPAAuthorizationService
 *
 * @author Jetski Team
 */
@ExtendWith(MockitoExtension.class)
class OPAAuthorizationServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private OPAAuthorizationService opaService;

    @BeforeEach
    void setUp() {
        // Setup WebClient mock chain
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        opaService = new OPAAuthorizationService(webClient);
    }

    // ========== RBAC Tests ==========

    @Test
    void shouldAuthorizeWhenOpaAllowsRBACAccess() {
        // Given
        OPAInput input = OPAInput.builder()
                .action("modelo:list")
                .user(OPAInput.UserContext.builder()
                        .id("user-1")
                        .tenant_id("T1")
                        .role("OPERADOR")
                        .build())
                .resource(OPAInput.ResourceContext.builder()
                        .tenant_id("T1")
                        .build())
                .build();

        OPAResponse<Boolean> mockResponse = new OPAResponse<>(true);

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(mockResponse));

        // When
        OPADecision decision = opaService.authorizeRBAC(input);

        // Then
        assertThat(decision).isNotNull();
        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.requiresApproval()).isFalse();

        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/v1/data/jetski/authz/rbac/allow");
    }

    @Test
    void shouldDenyWhenOpaDeniesRBACAccess() {
        // Given
        OPAInput input = OPAInput.builder()
                .action("modelo:create")
                .user(OPAInput.UserContext.builder()
                        .id("user-1")
                        .tenant_id("T1")
                        .role("OPERADOR")
                        .build())
                .resource(OPAInput.ResourceContext.builder()
                        .tenant_id("T1")
                        .build())
                .build();

        OPAResponse<Boolean> mockResponse = new OPAResponse<>(false);

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(mockResponse));

        // When
        OPADecision decision = opaService.authorizeRBAC(input);

        // Then
        assertThat(decision).isNotNull();
        assertThat(decision.isAllowed()).isFalse();
    }

    @Test
    void shouldHandleOpaRBACErrorGracefully() {
        // Given
        OPAInput input = OPAInput.builder()
                .action("modelo:list")
                .user(OPAInput.UserContext.builder()
                        .id("user-1")
                        .tenant_id("T1")
                        .role("OPERADOR")
                        .build())
                .resource(OPAInput.ResourceContext.builder()
                        .tenant_id("T1")
                        .build())
                .build();

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(new RuntimeException("OPA connection failed")));

        // When
        OPADecision decision = opaService.authorizeRBAC(input);

        // Then
        assertThat(decision).isNotNull();
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.isTenantValid()).isFalse();
    }

    // ========== Alçada Tests ==========

    @Test
    void shouldReturnApprovalRequiredForAlcada() {
        // Given
        OPAInput input = OPAInput.builder()
                .action("desconto:aplicar")
                .user(OPAInput.UserContext.builder()
                        .id("user-1")
                        .tenant_id("T1")
                        .role("GERENTE")
                        .build())
                .resource(OPAInput.ResourceContext.builder()
                        .id("loc-123")
                        .tenant_id("T1")
                        .type("locacao")
                        .build())
                .operation(OPAInput.OperationContext.builder()
                        .percentual_desconto(new BigDecimal("25"))
                        .build())
                .build();

        OPADecision mockDecision = OPADecision.builder()
                .allow(false)
                .requerAprovacao(true)
                .aprovadorRequerido("ADMIN_TENANT")
                .build();

        OPAResponse<OPADecision> mockResponse = new OPAResponse<>(mockDecision);

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(mockResponse));

        // When
        OPADecision decision = opaService.authorizeAlcada(input);

        // Then
        assertThat(decision).isNotNull();
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.requiresApproval()).isTrue();
        assertThat(decision.getAprovadorRequerido()).isEqualTo("ADMIN_TENANT");

        verify(requestBodyUriSpec).uri("/v1/data/jetski/authz/alcada");
    }

    @Test
    void shouldAllowAlcadaWhenWithinLimits() {
        // Given
        OPAInput input = OPAInput.builder()
                .action("desconto:aplicar")
                .user(OPAInput.UserContext.builder()
                        .id("user-1")
                        .tenant_id("T1")
                        .role("GERENTE")
                        .build())
                .resource(OPAInput.ResourceContext.builder()
                        .id("loc-123")
                        .tenant_id("T1")
                        .build())
                .operation(OPAInput.OperationContext.builder()
                        .percentual_desconto(new BigDecimal("10"))
                        .build())
                .build();

        OPADecision mockDecision = OPADecision.builder()
                .allow(true)
                .requerAprovacao(false)
                .build();

        OPAResponse<OPADecision> mockResponse = new OPAResponse<>(mockDecision);

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(mockResponse));

        // When
        OPADecision decision = opaService.authorizeAlcada(input);

        // Then
        assertThat(decision).isNotNull();
        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.requiresApproval()).isFalse();
    }

    @Test
    void shouldHandleOpaAlcadaErrorGracefully() {
        // Given
        OPAInput input = OPAInput.builder()
                .action("desconto:aplicar")
                .user(OPAInput.UserContext.builder()
                        .id("user-1")
                        .tenant_id("T1")
                        .role("GERENTE")
                        .build())
                .resource(OPAInput.ResourceContext.builder()
                        .tenant_id("T1")
                        .build())
                .operation(OPAInput.OperationContext.builder()
                        .percentual_desconto(new BigDecimal("15"))
                        .build())
                .build();

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(new RuntimeException("OPA connection failed")));

        // When
        OPADecision decision = opaService.authorizeAlcada(input);

        // Then
        assertThat(decision).isNotNull();
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.isTenantValid()).isFalse();
    }

    // ========== Generic authorize() Tests ==========

    @Test
    void shouldAuthorizeWithRBACOnlyWhenNoOperation() {
        // Given - No operation context
        OPAInput input = OPAInput.builder()
                .action("modelo:list")
                .user(OPAInput.UserContext.builder()
                        .id("user-1")
                        .tenant_id("T1")
                        .role("OPERADOR")
                        .build())
                .resource(OPAInput.ResourceContext.builder()
                        .tenant_id("T1")
                        .build())
                .build();

        OPADecision mockDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();
        OPAResponse<OPADecision> mockResponse = new OPAResponse<>(mockDecision);

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(mockResponse));

        // When
        OPADecision decision = opaService.authorize(input);

        // Then
        assertThat(decision).isNotNull();
        assertThat(decision.isAllowed()).isTrue();

        // Should call authorization endpoint
        verify(requestBodyUriSpec, times(1)).uri("/v1/data/jetski/authorization/result");
    }

    @Test
    void shouldCheckAlcadaWhenOperationContextPresent() {
        // Given - Has operation context
        OPAInput input = OPAInput.builder()
                .action("desconto:aplicar")
                .user(OPAInput.UserContext.builder()
                        .id("user-1")
                        .tenant_id("T1")
                        .role("GERENTE")
                        .build())
                .resource(OPAInput.ResourceContext.builder()
                        .tenant_id("T1")
                        .build())
                .operation(OPAInput.OperationContext.builder()
                        .percentual_desconto(new BigDecimal("15"))
                        .build())
                .build();

        // Authorization endpoint returns complete decision
        OPADecision mockDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .requerAprovacao(false)
                .build();
        OPAResponse<OPADecision> mockResponse = new OPAResponse<>(mockDecision);

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(mockResponse));

        // When
        OPADecision decision = opaService.authorize(input);

        // Then
        assertThat(decision).isNotNull();
        assertThat(decision.isAllowed()).isTrue();

        // Should call authorization endpoint
        verify(requestBodyUriSpec, times(1)).uri("/v1/data/jetski/authorization/result");
    }

    @Test
    void shouldNotCheckAlcadaWhenRBACDenies() {
        // Given
        OPAInput input = OPAInput.builder()
                .action("desconto:aplicar")
                .user(OPAInput.UserContext.builder()
                        .id("user-1")
                        .tenant_id("T1")
                        .role("OPERADOR")  // Operador não pode aplicar desconto
                        .build())
                .resource(OPAInput.ResourceContext.builder()
                        .tenant_id("T1")
                        .build())
                .operation(OPAInput.OperationContext.builder()
                        .percentual_desconto(new BigDecimal("15"))
                        .build())
                .build();

        // Authorization endpoint returns deny decision
        OPADecision mockDecision = OPADecision.builder()
                .allow(false)
                .tenantIsValid(true)
                .build();
        OPAResponse<OPADecision> mockResponse = new OPAResponse<>(mockDecision);

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(mockResponse));

        // When
        OPADecision decision = opaService.authorize(input);

        // Then
        assertThat(decision).isNotNull();
        assertThat(decision.isAllowed()).isFalse();

        // Should call authorization endpoint
        verify(requestBodyUriSpec, times(1)).uri("/v1/data/jetski/authorization/result");
    }

    // ========== Edge Case Tests ==========

    @Test
    void shouldHandleNullResponseFromOpaRBAC() {
        // Given
        OPAInput input = OPAInput.builder()
                .action("modelo:list")
                .user(OPAInput.UserContext.builder()
                        .id("user-1")
                        .tenant_id("T1")
                        .role("OPERADOR")
                        .build())
                .resource(OPAInput.ResourceContext.builder()
                        .tenant_id("T1")
                        .build())
                .build();

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.empty());

        // When
        OPADecision decision = opaService.authorizeRBAC(input);

        // Then
        assertThat(decision).isNotNull();
        assertThat(decision.isAllowed()).isFalse();
    }

    @Test
    void shouldHandleNullResponseFromOpaAlcada() {
        // Given
        OPAInput input = OPAInput.builder()
                .action("desconto:aplicar")
                .user(OPAInput.UserContext.builder()
                        .id("user-1")
                        .tenant_id("T1")
                        .role("GERENTE")
                        .build())
                .resource(OPAInput.ResourceContext.builder()
                        .tenant_id("T1")
                        .build())
                .operation(OPAInput.OperationContext.builder()
                        .percentual_desconto(new BigDecimal("15"))
                        .build())
                .build();

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.empty());

        // When
        OPADecision decision = opaService.authorizeAlcada(input);

        // Then
        assertThat(decision).isNotNull();
        assertThat(decision.isAllowed()).isFalse();
    }

    @Test
    void shouldHandleTenantMismatchInRBAC() {
        // Given - User from T1 trying to access resource in T2
        OPAInput input = OPAInput.builder()
                .action("modelo:list")
                .user(OPAInput.UserContext.builder()
                        .id("user-1")
                        .tenant_id("T1")
                        .role("OPERADOR")
                        .build())
                .resource(OPAInput.ResourceContext.builder()
                        .tenant_id("T2")
                        .build())
                .build();

        OPAResponse<Boolean> mockResponse = new OPAResponse<>(false);

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(mockResponse));

        // When
        OPADecision decision = opaService.authorizeRBAC(input);

        // Then
        assertThat(decision).isNotNull();
        assertThat(decision.isAllowed()).isFalse();
    }

    @Test
    void shouldHandleHighValueOperationRequiringApproval() {
        // Given - High value discount requiring admin approval
        OPAInput input = OPAInput.builder()
                .action("desconto:aplicar")
                .user(OPAInput.UserContext.builder()
                        .id("user-1")
                        .tenant_id("T1")
                        .role("GERENTE")
                        .build())
                .resource(OPAInput.ResourceContext.builder()
                        .tenant_id("T1")
                        .build())
                .operation(OPAInput.OperationContext.builder()
                        .percentual_desconto(new BigDecimal("50"))  // High discount
                        .build())
                .build();

        OPADecision mockDecision = OPADecision.builder()
                .allow(false)
                .requerAprovacao(true)
                .aprovadorRequerido("ADMIN_TENANT")
                .build();

        OPAResponse<OPADecision> mockResponse = new OPAResponse<>(mockDecision);

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(mockResponse));

        // When
        OPADecision decision = opaService.authorizeAlcada(input);

        // Then
        assertThat(decision).isNotNull();
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.requiresApproval()).isTrue();
        assertThat(decision.getAprovadorRequerido()).isEqualTo("ADMIN_TENANT");
    }
}
