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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Testes unitários para KeycloakHealthIndicator.
 *
 * Valida que o health indicator reporta corretamente:
 * - UP quando Keycloak está acessível
 * - DOWN quando Keycloak está inacessível ou retorna resposta inválida
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KeycloakHealthIndicator Unit Tests")
class KeycloakHealthIndicatorTest {

    @Mock
    private RestTemplate restTemplate;

    private KeycloakHealthIndicator healthIndicator;

    private static final String TEST_ISSUER_URI = "http://localhost:8081/realms/jetski-saas";

    @BeforeEach
    void setUp() {
        healthIndicator = new KeycloakHealthIndicator();
        ReflectionTestUtils.setField(healthIndicator, "issuerUri", TEST_ISSUER_URI);
        ReflectionTestUtils.setField(healthIndicator, "restTemplate", restTemplate);
    }

    @Test
    @DisplayName("Should return UP when Keycloak is accessible and returns valid realm response")
    void testHealth_Success_KeycloakIsUp() {
        // Given: Keycloak retorna resposta válida contendo "realm"
        String validResponse = "{\"realm\":\"jetski-saas\",\"public_key\":\"...\"}";
        when(restTemplate.getForObject(eq(TEST_ISSUER_URI), eq(String.class)))
                .thenReturn(validResponse);

        // When: health check é executado
        Health health = healthIndicator.health();

        // Then: status deve ser UP com detalhes corretos
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("realm", "jetski-saas");
        assertThat(health.getDetails()).containsEntry("issuerUri", TEST_ISSUER_URI);
        assertThat(health.getDetails()).containsKey("responseTime");
        assertThat(health.getDetails()).containsEntry("status", "Keycloak realm is accessible");
    }

    @Test
    @DisplayName("Should return DOWN when Keycloak is unreachable")
    void testHealth_Failure_KeycloakDown() {
        // Given: Keycloak não responde (exceção)
        when(restTemplate.getForObject(eq(TEST_ISSUER_URI), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));

        // When: health check é executado
        Health health = healthIndicator.health();

        // Then: status deve ser DOWN com detalhes do erro
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("issuerUri", TEST_ISSUER_URI);
        assertThat(health.getDetails()).containsKey("responseTime");
        assertThat(health.getDetails()).containsEntry("error", "Connection refused");
        assertThat(health.getDetails()).containsEntry("errorType", "RestClientException");
    }

    @Test
    @DisplayName("Should return DOWN when Keycloak returns invalid response")
    void testHealth_Failure_InvalidResponse() {
        // Given: Keycloak retorna resposta inválida (não contém "realm")
        String invalidResponse = "{\"error\":\"invalid_request\"}";
        when(restTemplate.getForObject(eq(TEST_ISSUER_URI), eq(String.class)))
                .thenReturn(invalidResponse);

        // When: health check é executado
        Health health = healthIndicator.health();

        // Then: status deve ser DOWN
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("realm", "jetski-saas");
        assertThat(health.getDetails()).containsEntry("issuerUri", TEST_ISSUER_URI);
        assertThat(health.getDetails()).containsKey("responseTime");
        assertThat(health.getDetails()).containsEntry("error", "Unexpected response from Keycloak");
    }

    @Test
    @DisplayName("Should return DOWN when Keycloak returns null response")
    void testHealth_Failure_NullResponse() {
        // Given: Keycloak retorna null
        when(restTemplate.getForObject(eq(TEST_ISSUER_URI), eq(String.class)))
                .thenReturn(null);

        // When: health check é executado
        Health health = healthIndicator.health();

        // Then: status deve ser DOWN
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", "Unexpected response from Keycloak");
    }

    @Test
    @DisplayName("Should extract realm name correctly from issuer URI")
    void testExtractRealmName_Success() {
        // Given: Diferentes formatos de issuer URI
        String testUri = "http://localhost:8081/realms/jetski-saas";
        ReflectionTestUtils.setField(healthIndicator, "issuerUri", testUri);
        String validResponse = "{\"realm\":\"jetski-saas\"}";
        when(restTemplate.getForObject(eq(testUri), eq(String.class))).thenReturn(validResponse);

        // When: health check é executado
        Health health = healthIndicator.health();

        // Then: realm name deve ser extraído corretamente
        assertThat(health.getDetails()).containsEntry("realm", "jetski-saas");
    }

    @Test
    @DisplayName("Should handle realm extraction from different URI formats")
    void testExtractRealmName_DifferentFormats() {
        // Given: URI sem trailing slash
        String testUri = "http://keycloak.example.com/realms/my-realm";
        ReflectionTestUtils.setField(healthIndicator, "issuerUri", testUri);
        String validResponse = "{\"realm\":\"test\"}";
        when(restTemplate.getForObject(eq(testUri), eq(String.class))).thenReturn(validResponse);

        // When: health check é executado
        Health health = healthIndicator.health();

        // Then: realm name deve ser "my-realm" (extraído da URL)
        assertThat(health.getDetails()).containsEntry("realm", "my-realm");
    }
}
