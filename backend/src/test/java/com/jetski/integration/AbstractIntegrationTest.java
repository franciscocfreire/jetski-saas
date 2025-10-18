package com.jetski.integration;

import com.jetski.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests using Testcontainers.
 *
 * Provides:
 * - PostgreSQL container for database tests (singleton across all tests)
 * - Spring Boot context with test profile
 * - Common setup/teardown
 *
 * @author Jetski Team
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    // Singleton PostgreSQL container shared across all integration tests
    protected static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("jetski_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Disable Keycloak for integration tests
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "http://localhost:9999");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:9999/certs");
    }

    @AfterEach
    void cleanupTenantContext() {
        TenantContext.clear();
    }
}
