package com.jetski.shared.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Keycloak Health Indicator
 *
 * Checks the health of the Keycloak authentication server by calling its realm endpoint.
 * This is critical for user authentication and authorization.
 *
 * Health states:
 * - UP: Keycloak is reachable and realm is accessible
 * - DOWN: Keycloak is unreachable or realm not found
 *
 * Additional details included:
 * - realm: Configured realm name
 * - issuerUri: Full issuer URI
 * - responseTime: Time taken to respond (ms)
 *
 * @author Jetski Team
 * @since 0.7.5
 */
@Component("keycloak")
public class KeycloakHealthIndicator implements HealthIndicator {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    private final RestTemplate restTemplate;
    private static final int TIMEOUT_MS = 3000;

    public KeycloakHealthIndicator() {
        this.restTemplate = new RestTemplate();
        // Set timeouts
        this.restTemplate.getRequestFactory();
    }

    @Override
    public Health health() {
        long startTime = System.currentTimeMillis();

        try {
            // Extract realm name from issuer URI
            String realmName = extractRealmName(issuerUri);

            // Call Keycloak realm endpoint (public endpoint, no auth needed)
            String realmUrl = issuerUri; // The issuer URI itself is a public endpoint

            String response = restTemplate.getForObject(realmUrl, String.class);

            long responseTime = System.currentTimeMillis() - startTime;

            if (response != null && response.contains("realm")) {
                return Health.up()
                        .withDetail("realm", realmName)
                        .withDetail("issuerUri", issuerUri)
                        .withDetail("responseTime", responseTime + "ms")
                        .withDetail("status", "Keycloak realm is accessible")
                        .build();
            } else {
                return Health.down()
                        .withDetail("realm", realmName)
                        .withDetail("issuerUri", issuerUri)
                        .withDetail("responseTime", responseTime + "ms")
                        .withDetail("error", "Unexpected response from Keycloak")
                        .build();
            }

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return Health.down()
                    .withDetail("issuerUri", issuerUri)
                    .withDetail("responseTime", responseTime + "ms")
                    .withDetail("error", e.getMessage())
                    .withDetail("errorType", e.getClass().getSimpleName())
                    .build();
        }
    }

    /**
     * Extract realm name from issuer URI
     * Example: http://localhost:8081/realms/jetski-saas -> jetski-saas
     */
    private String extractRealmName(String issuerUri) {
        if (issuerUri == null) {
            return "unknown";
        }
        String[] parts = issuerUri.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : "unknown";
    }
}
