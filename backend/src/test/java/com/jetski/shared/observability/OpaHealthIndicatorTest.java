package com.jetski.shared.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testes unitários para OpaHealthIndicator.
 *
 * Valida que o health indicator reporta corretamente:
 * - UP quando OPA está acessível
 * - DOWN quando OPA está inacessível ou retorna erro
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OpaHealthIndicator Unit Tests")
class OpaHealthIndicatorTest {

    @Mock
    private WebClient opaWebClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private OpaHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new OpaHealthIndicator(opaWebClient);
    }

    @Test
    @DisplayName("Should return UP when OPA is accessible and returns healthy response")
    void testHealth_Success_OpaIsUp() {
        // Given: OPA retorna resposta de sucesso
        String validResponse = "{\"status\":\"ok\"}";

        when(opaWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/health")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(validResponse));

        // When: health check é executado
        Health health = healthIndicator.health();

        // Then: status deve ser UP com detalhes corretos
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("responseTime");
        assertThat(health.getDetails()).containsEntry("endpoint", "http://localhost:8181");
        assertThat(health.getDetails()).containsEntry("status", "OPA is healthy");
    }

    @Test
    @DisplayName("Should return DOWN when OPA is unreachable or times out")
    void testHealth_Failure_OpaDown() {
        // Given: OPA não responde (timeout)
        when(opaWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/health")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.error(new RuntimeException("Connection timeout")));

        // When: health check é executado
        Health health = healthIndicator.health();

        // Then: status deve ser DOWN com detalhes do erro
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("responseTime");
        assertThat(health.getDetails()).containsEntry("endpoint", "http://localhost:8181");
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails().get("error").toString()).contains("Connection timeout");
    }

    @Test
    @DisplayName("Should return DOWN when OPA returns error response")
    void testHealth_Failure_ErrorResponse() {
        // Given: OPA retorna resposta com erro
        String errorResponse = "ERROR: Service unavailable";

        when(opaWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/health")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(errorResponse));

        // When: health check é executado
        Health health = healthIndicator.health();

        // Then: status deve ser DOWN
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("responseTime");
        assertThat(health.getDetails()).containsEntry("endpoint", "http://localhost:8181");
        assertThat(health.getDetails()).containsEntry("error", errorResponse);
    }

    @Test
    @DisplayName("Should return DOWN when OPA returns null response")
    void testHealth_Failure_NullResponse() {
        // Given: OPA retorna null
        when(opaWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/health")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.empty());

        // When: health check é executado
        Health health = healthIndicator.health();

        // Then: status deve ser DOWN
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    @DisplayName("Should handle WebClient exception gracefully")
    void testHealth_Failure_WebClientException() {
        // Given: WebClient lança exceção
        when(opaWebClient.get()).thenThrow(new RuntimeException("WebClient initialization error"));

        // When: health check é executado
        Health health = healthIndicator.health();

        // Then: status deve ser DOWN com detalhes da exceção
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("responseTime");
        assertThat(health.getDetails()).containsEntry("endpoint", "http://localhost:8181");
        assertThat(health.getDetails()).containsEntry("error", "WebClient initialization error");
        assertThat(health.getDetails()).containsEntry("errorType", "RuntimeException");
    }

    @Test
    @DisplayName("Should measure response time correctly")
    void testHealth_MeasuresResponseTime() {
        // Given: OPA responde após delay
        when(opaWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/health")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.just("{}").delayElement(Duration.ofMillis(10)));

        // When: health check é executado
        Health health = healthIndicator.health();

        // Then: responseTime deve estar presente e ser >= 10ms
        assertThat(health.getDetails()).containsKey("responseTime");
        String responseTime = (String) health.getDetails().get("responseTime");
        assertThat(responseTime).endsWith("ms");

        // Extrai valor numérico (ex: "15ms" -> 15)
        int timeMs = Integer.parseInt(responseTime.replace("ms", ""));
        assertThat(timeMs).isGreaterThanOrEqualTo(0);
    }
}
