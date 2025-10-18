package com.jetski.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Flyway database migrations.
 *
 * Verifies that:
 * - All Flyway migrations execute successfully
 * - Database schema is created correctly
 * - All expected tables exist
 *
 * @author Jetski Team
 */
class FlywayMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Test
    void shouldExecuteAllMigrationsSuccessfully() {
        // When
        var info = flyway.info();

        // Then
        assertThat(info.all()).isNotEmpty();
        assertThat(info.pending()).isEmpty();
    }

    @Test
    void shouldCreateAllExpectedTables() {
        // Given
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // Expected multi-tenant tables
        List<String> expectedTables = List.of(
                "tenant",
                "plano",
                "assinatura",
                "usuario",
                "membro",
                // Operational tables
                "modelo",
                "jetski",
                "vendedor",
                "cliente",
                "reserva",
                "locacao",
                "foto",
                "abastecimento",
                "os_manutencao",
                // Support tables
                "commission_policy",
                "fuel_policy",
                "fuel_price_day",
                "fechamento_diario",
                "fechamento_mensal",
                "auditoria"
        );

        // When
        List<String> actualTables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class
        );

        // Then
        assertThat(actualTables)
                .as("All expected tables should be created by Flyway migrations")
                .containsAll(expectedTables);
    }

    @Test
    void shouldHaveTenantIdColumnInAllOperationalTables() {
        // Given
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        List<String> operationalTables = List.of(
                "modelo", "jetski", "vendedor", "cliente", "reserva", "locacao",
                "foto", "abastecimento", "os_manutencao", "commission_policy",
                "fuel_policy", "fechamento_diario", "fechamento_mensal", "auditoria"
        );

        // When/Then
        for (String table : operationalTables) {
            List<String> columns = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns " +
                            "WHERE table_schema = 'public' AND table_name = ?",
                    String.class,
                    table
            );

            assertThat(columns)
                    .as("Table %s should have tenant_id column for multi-tenant isolation", table)
                    .contains("tenant_id");
        }
    }

    @Test
    void shouldHaveIndexesOnTenantIdColumns() {
        // Given
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // When - Use PostgreSQL-specific query to find indexes
        List<String> indexedColumns = jdbcTemplate.queryForList(
                "SELECT DISTINCT tablename || '.' || indexdef " +
                        "FROM pg_indexes " +
                        "WHERE schemaname = 'public' " +
                        "AND indexdef LIKE '%tenant_id%'",
                String.class
        );

        // Then
        assertThat(indexedColumns)
                .as("tenant_id columns should have indexes for query performance")
                .isNotEmpty();
    }

    @Test
    void shouldHaveSeedDataInDevMode() {
        // Given
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // When - Check if seed data exists
        Integer tenantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant",
                Integer.class
        );

        // Then
        assertThat(tenantCount)
                .as("Seed data should create at least one tenant")
                .isGreaterThan(0);
    }

    @Test
    void shouldHavePrimaryKeysOnAllTables() {
        // Given
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        List<String> allTables = List.of(
                "tenant", "plano", "assinatura", "usuario", "membro",
                "modelo", "jetski", "vendedor", "cliente", "reserva",
                "locacao", "foto", "abastecimento", "os_manutencao"
        );

        // When/Then
        for (String table : allTables) {
            Integer pkCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.table_constraints " +
                            "WHERE table_schema = 'public' " +
                            "AND table_name = ? " +
                            "AND constraint_type = 'PRIMARY KEY'",
                    Integer.class,
                    table
            );

            assertThat(pkCount)
                    .as("Table %s should have a primary key", table)
                    .isEqualTo(1);
        }
    }

    @Test
    void shouldHaveForeignKeyConstraintsForMultiTenancy() {
        // Given
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // When
        List<String> foreignKeys = jdbcTemplate.queryForList(
                "SELECT tc.table_name || ' -> ' || ccu.table_name as fk_relation " +
                        "FROM information_schema.table_constraints AS tc " +
                        "JOIN information_schema.key_column_usage AS kcu " +
                        "ON tc.constraint_name = kcu.constraint_name " +
                        "AND tc.table_schema = kcu.table_schema " +
                        "JOIN information_schema.constraint_column_usage AS ccu " +
                        "ON ccu.constraint_name = tc.constraint_name " +
                        "AND ccu.table_schema = tc.table_schema " +
                        "WHERE tc.constraint_type = 'FOREIGN KEY' " +
                        "AND tc.table_schema = 'public'",
                String.class
        );

        // Then
        assertThat(foreignKeys)
                .as("Should have foreign key relationships for referential integrity")
                .isNotEmpty();
    }
}
