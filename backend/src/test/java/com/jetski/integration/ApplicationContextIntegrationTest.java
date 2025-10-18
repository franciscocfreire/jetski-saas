package com.jetski.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Spring Boot application context.
 *
 * Verifies that:
 * - Application context loads successfully
 * - All beans are wired correctly
 * - Testcontainers PostgreSQL is accessible
 *
 * @author Jetski Team
 */
class ApplicationContextIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldLoadApplicationContext() {
        // Then
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void shouldHavePostgresContainerRunning() {
        // Then
        assertThat(postgres.isRunning()).isTrue();
        assertThat(postgres.getDatabaseName()).isEqualTo("jetski_test");
    }

    @Test
    void shouldConfigureDataSourceFromContainer() {
        // Then
        assertThat(postgres.getJdbcUrl()).contains("jdbc:postgresql://");
        assertThat(postgres.getUsername()).isEqualTo("test");
    }
}
