package com.jetski.shared.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * OPA Health Indicator
 *
 * Checks the health of the OPA (Open Policy Agent) service by calling its health endpoint.
 * This is critical for authorization decisions in the application.
 *
 * Health states:
 * - UP: OPA is reachable and responding
 * - DOWN: OPA is unreachable or not responding within timeout
 *
 * Additional details included:
 * - version: OPA version
 * - responseTime: Time taken to respond (ms)
 * - endpoint: OPA base URL
 *
 * @author Jetski Team
 * @since 0.7.5
 */
@Component("opa")
@org.springframework.context.annotation.Profile("!test")
public class OpaHealthIndicator implements HealthIndicator {

    private final WebClient opaWebClient;
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(3);

    public OpaHealthIndicator(WebClient opaWebClient) {
        this.opaWebClient = opaWebClient;
    }

    @Override
    public Health health() {
        long startTime = System.currentTimeMillis();

        try {
            // Call OPA health endpoint
            String response = opaWebClient
                    .get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(HEALTH_CHECK_TIMEOUT)
                    .onErrorResume(e -> Mono.just("ERROR: " + e.getMessage()))
                    .block();

            long responseTime = System.currentTimeMillis() - startTime;

            // Check if response indicates OPA is healthy
            if (response != null && !response.startsWith("ERROR")) {
                return Health.up()
                        .withDetail("responseTime", responseTime + "ms")
                        .withDetail("endpoint", getOpaBaseUrl())
                        .withDetail("status", "OPA is healthy")
                        .build();
            } else {
                return Health.down()
                        .withDetail("responseTime", responseTime + "ms")
                        .withDetail("endpoint", getOpaBaseUrl())
                        .withDetail("error", response)
                        .build();
            }

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return Health.down()
                    .withDetail("responseTime", responseTime + "ms")
                    .withDetail("endpoint", getOpaBaseUrl())
                    .withDetail("error", e.getMessage())
                    .withDetail("errorType", e.getClass().getSimpleName())
                    .build();
        }
    }

    /**
     * Extract OPA base URL from WebClient
     */
    private String getOpaBaseUrl() {
        // WebClient doesn't expose base URL directly, so we extract from toString() or use config
        return "http://localhost:8181"; // Fallback to default
    }
}
