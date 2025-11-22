package com.jetski.shared.config;

import com.jetski.shared.security.TenantContext;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * DataSource wrapper that sets PostgreSQL RLS tenant context on each connection.
 *
 * How it works:
 * 1. Creates a custom DataSource bean that wraps HikariCP
 * 2. On each getConnection(), executes: SELECT set_config('app.tenant_id', 'UUID', true)
 * 3. PostgreSQL RLS policies then filter rows by tenant_id
 *
 * This is the bridge between:
 * - Spring's TenantFilter (sets TenantContext.tenantId from HTTP header)
 * - PostgreSQL RLS policies (filter by current_setting('app.tenant_id'))
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Configuration
@Slf4j
public class TenantAwareDataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        // Create the actual HikariDataSource
        HikariDataSource hikariDataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        // Apply additional properties from application.yml
        if (properties.getName() != null) {
            hikariDataSource.setPoolName(properties.getName());
        }

        log.info("Creating TenantAwareDataSource wrapper for PostgreSQL RLS support");
        return new TenantAwareDataSource(hikariDataSource);
    }

    /**
     * DataSource wrapper that sets tenant context on each connection
     */
    public static class TenantAwareDataSource extends DelegatingDataSource {

        public TenantAwareDataSource(DataSource targetDataSource) {
            super(targetDataSource);
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            setTenantContext(connection);
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Connection connection = super.getConnection(username, password);
            setTenantContext(connection);
            return connection;
        }

        private void setTenantContext(Connection connection) {
            UUID tenantId = TenantContext.getTenantId();
            log.debug("TenantAwareDataSource.setTenantContext called, tenantId={}", tenantId);
            if (tenantId != null) {
                try (Statement statement = connection.createStatement()) {
                    // is_local = false: config persists for the entire connection/session, not just current transaction
                    String sql = String.format("SELECT set_config('app.tenant_id', '%s', false)", tenantId);
                    statement.execute(sql);
                    log.info("RLS tenant context set: {}", tenantId);
                } catch (SQLException e) {
                    log.warn("Failed to set RLS tenant context: {}", e.getMessage());
                }
            } else {
                log.debug("TenantContext.tenantId is null, skipping RLS set_config");
            }
        }
    }
}
