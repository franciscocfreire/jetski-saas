package com.jetski.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base do harness NÃO-superuser: o contexto Spring destas classes conecta como
 * {@code app_test} (LOGIN, NOSUPERUSER, NOBYPASSRLS), espelho do
 * {@code jetski_app} de produção — a RLS VALE de verdade. As migrations
 * continuam rodando como o superuser (dono do schema), como em produção.
 *
 * <p><b>Por que existe</b>: o resto da suíte conecta como superuser e é cega a
 * toda a classe de bug "código sem contexto RLS" — três chegaram a produção em
 * 28/jul/2026 sem nenhum dos ~1200 testes piscar. Fluxos de plataforma
 * ({@link PlatformNonSuperuserIntegrationTest}) e do escopo customer
 * ({@link CustomerNonSuperuserIntegrationTest}) ganham aqui o teste que
 * reproduz o ambiente real.
 *
 * <p>As subclasses COMPARTILHAM este contexto (mesmo {@code @DynamicPropertySource}
 * herdado, sem {@code @MockBean} próprios — cada combinação nova custaria um
 * pool Hikari extra no Postgres de teste).
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractNonSuperuserIntegrationTest {

    protected static final String APP_ROLE = "app_test";

    @DynamicPropertySource
    static void nonSuperuserDatasource(DynamicPropertyRegistry registry) {
        // Referenciar AbstractIntegrationTest dispara o static-init dos
        // containers singleton — mesmo Postgres/Redis do resto da suíte.
        var postgres = AbstractIntegrationTest.postgres;
        var redis = AbstractIntegrationTest.redis;

        criarRoleApp();

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_ROLE);
        // Migrations como superuser (dono do schema), igual produção. A url
        // precisa vir junto: só user/password não faz o Flyway derivar a do
        // datasource ("Unable to find suitable method for url").
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "http://localhost:9999");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:9999/certs");
    }

    /**
     * Role da aplicação + grants, ANTES do contexto subir. O DEFAULT PRIVILEGES
     * (para o role que roda as migrations) cobre tabelas criadas depois — as
     * classes funcionam rodando antes ou depois do resto da suíte.
     */
    private static void criarRoleApp() {
        try (Connection c = superConnection(); Statement st = c.createStatement()) {
            st.execute("""
                DO $$ BEGIN
                  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_test') THEN
                    CREATE ROLE app_test LOGIN PASSWORD 'app_test' NOSUPERUSER NOBYPASSRLS;
                  END IF;
                END $$;
                """);
            st.execute("GRANT USAGE ON SCHEMA public TO app_test");
            st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_test");
            // UPDATE nas sequences: setval do import (mesmo grant da V058)
            st.execute("GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO app_test");
            st.execute("ALTER DEFAULT PRIVILEGES FOR ROLE test IN SCHEMA public "
                + "GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_test");
            st.execute("ALTER DEFAULT PRIVILEGES FOR ROLE test IN SCHEMA public "
                + "GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO app_test");
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao preparar role app_test", e);
        }
    }

    /** Conexão superuser à parte — seeds/asserts não podem depender da RLS testada. */
    protected static Connection superConnection() throws SQLException {
        var postgres = AbstractIntegrationTest.postgres;
        return DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
