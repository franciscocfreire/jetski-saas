package com.jetski.integration;

import com.jetski.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests using Testcontainers.
 *
 * Provides:
 * - PostgreSQL container for database tests (singleton across all tests)
 * - Redis container (cache + health) — sem ele, o caminho @Cacheable do
 *   TenantFilter estoura e todo endpoint vira 500 no CI (que não tem Redis local)
 * - Spring Boot context com profile test
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
    // Singleton Redis container (cache do TenantFilter/identidade + RedisTemplate)
    protected static final GenericContainer<?> redis;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("jetski_test")
                .withUsername("test")
                .withPassword("test")
                // cada contexto Spring cacheado mantém um pool Hikari vivo e a
                // suíte inteira estoura os 100 max_connections default ("too
                // many clients"). withCommand NÃO adianta: PostgreSQLContainer
                // .configure() sobrescreve o cmd no start — só o modifier fica.
                .withCreateContainerCmdModifier(cmd -> cmd.withCmd(
                    "postgres", "-c", "fsync=off", "-c", "max_connections=400"));
        postgres.start();

        redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        redis.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis (Testcontainers) — provê cache e health p/ os testes de integração
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        // Disable Keycloak for integration tests
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "http://localhost:9999");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:9999/certs");
    }

    @AfterEach
    void cleanupTenantContext() {
        TenantContext.clear();
    }
}
