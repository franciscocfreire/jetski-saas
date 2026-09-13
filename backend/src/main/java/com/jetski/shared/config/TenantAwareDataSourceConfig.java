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

        /**
         * Fixa (ou limpa) os GUCs de RLS na conexão recém-obtida do pool.
         *
         * <p><b>Fail-closed</b>: se qualquer statement falhar, a conexão NÃO é entregue.
         * O GUC é de sessão ({@code is_local = false}) e o Hikari reusa conexões — uma
         * falha aqui deixaria na conexão o {@code app.tenant_id} da requisição ANTERIOR,
         * e a requisição corrente leria/escreveria sob a RLS de outro tenant. Por isso a
         * conexão é despejada do pool (não volta para ser reusada) e a falha sobe como
         * {@link SQLException} — melhor um 500 do que um vazamento cross-tenant.
         */
        private void setTenantContext(Connection connection) throws SQLException {
            UUID tenantId = TenantContext.getTenantId();
            boolean unrestricted = TenantContext.isUnrestricted();
            log.debug("TenantAwareDataSource.setTenantContext called, tenantId={}", tenantId);
            try (Statement statement = connection.createStatement()) {
                if (tenantId != null) {
                    // is_local = false: config persists for the entire connection/session, not just current transaction
                    String sql = String.format("SELECT set_config('app.tenant_id', '%s', false)", tenantId);
                    statement.execute(sql);
                    // DEBUG: roda a CADA checkout de conexão do pool (toda query) —
                    // em INFO isso vira uma linha por query no Loki
                    log.debug("RLS tenant context set: {}", tenantId);
                } else {
                    // IMPORTANT: Reset tenant context for public endpoints (marketplace, etc.)
                    // HikariCP reuses connections, so we must clear any previous tenant context
                    // Using RESET clears the setting to its default (empty/null)
                    statement.execute("RESET app.tenant_id");
                    log.debug("RLS tenant context cleared for public access");
                }
                // Superadmin de plataforma: a policy da tabela tenant (V042) libera
                // todas as linhas quando app.unrestricted = 'true' (opera com
                // X-Tenant-Id mas lista/gerencia todos os tenants). Sempre setar/
                // limpar — HikariCP reusa conexões.
                statement.execute(String.format(
                    "SELECT set_config('app.unrestricted', '%s', false)",
                    unrestricted ? "true" : ""));
            } catch (SQLException | RuntimeException e) {
                log.error("Falha ao fixar o contexto RLS da conexão (tenantId={}, unrestricted={}) — "
                    + "conexão descartada, requisição falha fechada: {}", tenantId, unrestricted, e.getMessage());
                descartar(connection);
                throw new SQLException(
                    "Falha ao estabelecer o contexto RLS (app.tenant_id) na conexão — conexão descartada",
                    e instanceof SQLException sql ? sql.getSQLState() : null, e);
            }
        }

        /**
         * Tira a conexão de circulação: no Hikari, {@code evictConnection} fecha a conexão
         * física e a remove do pool (um simples {@code close()} a devolveria para reuso,
         * com o GUC obsoleto). Para outros DataSources, fecha.
         */
        private void descartar(Connection connection) {
            try {
                if (obtainTargetDataSource() instanceof HikariDataSource hikari) {
                    hikari.evictConnection(connection);
                }
            } catch (RuntimeException ex) {
                log.warn("Falha ao despejar conexão do pool: {}", ex.getMessage());
            }
            try {
                connection.close();
            } catch (SQLException | RuntimeException ex) {
                log.warn("Falha ao fechar conexão descartada: {}", ex.getMessage());
            }
        }
    }
}
