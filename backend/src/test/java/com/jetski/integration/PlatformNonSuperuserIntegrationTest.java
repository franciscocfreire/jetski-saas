package com.jetski.integration;

import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.internal.PlatformFaturaService;
import com.jetski.tenant.internal.TenantImportService;
import com.jetski.tenant.internal.TenantResetService;
import com.jetski.tenant.internal.TenantResetService.Nivel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Harness NÃO-superuser para os fluxos de plataforma — o contexto Spring desta
 * classe conecta como {@code app_test} (LOGIN, NOSUPERUSER, NOBYPASSRLS),
 * espelho do {@code jetski_app} de produção. As migrations continuam rodando
 * como o superuser (dono do schema), como em produção.
 *
 * <p><b>Por que existe</b>: a suíte inteira conecta como superuser e a RLS não
 * vale — em 28/jul/2026 TRÊS bugs da mesma família chegaram a produção sem que
 * nenhum dos ~1200 testes piscasse: a trilha de auditoria de eventos de
 * plataforma bloqueada pela RLS (perdida em silêncio), o audit dual da emissão
 * delegada com o mesmo defeito no tenant remoto, e o mudarPlano sem contexto
 * estourando 500 no primeiro uso via console. Cada teste aqui reproduz o estado
 * REAL de uma rota de plataforma: {@code TenantContext} sem tenant, com
 * {@code unrestricted=true} (o que o TenantFilter deixa para o console).
 *
 * <p>Seeds e asserts de auditoria usam uma conexão superuser à parte — seed
 * cross-tenant e leitura de trilha não podem depender da RLS que está sendo
 * testada.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Plataforma sob role não-superuser (RLS valendo de verdade)")
class PlatformNonSuperuserIntegrationTest {

    private static final String APP_ROLE = "app_test";
    private static final UUID TENANT = UUID.fromString("a4000000-0000-0000-0000-0000000000aa");
    private static final String SLUG = "nonsuper-teste";

    @Autowired private TenantResetService resetService;
    @Autowired private TenantImportService importService;
    @Autowired private PlatformFaturaService faturaService;
    @Autowired private JdbcTemplate jdbc; // conecta como app_test

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
     * (para o role que roda as migrations) cobre tabelas criadas depois — esta
     * classe funciona rodando primeiro ou depois do resto da suíte.
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

    private static Connection superConnection() throws SQLException {
        var postgres = AbstractIntegrationTest.postgres;
        return DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    // ------------------------------------------------------------------
    // Fixture (superuser: seed cross-tenant não pode depender da RLS testada)
    // ------------------------------------------------------------------

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection c = superConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO tenant (id, slug, razao_social, status) VALUES ('" + TENANT
                + "', '" + SLUG + "', 'NonSuper Teste Ltda', 'ATIVO') ON CONFLICT DO NOTHING");
            st.execute("INSERT INTO modelo (id, tenant_id, nome, fabricante, preco_base_hora, ativo) "
                + "VALUES ('a4000000-0000-0000-0000-000000000001', '" + TENANT
                + "', 'NonSuper Modelo', 'Yamaha', 100, true) ON CONFLICT DO NOTHING");
            st.execute("INSERT INTO cliente (id, tenant_id, nome, documento, ativo) "
                + "VALUES ('a4000000-0000-0000-0000-000000000003', '" + TENANT
                + "', 'Cliente NonSuper', '222.333.444-55', true) ON CONFLICT DO NOTHING");
            // Ator dos eventos: auditoria.usuario_id tem FK — fica no banco
            // (como o tenant), a trilha gerada nos testes o referencia.
            st.execute("INSERT INTO usuario (id, email, nome, ativo) "
                + "VALUES ('a4000000-0000-0000-0000-00000000ad01', 'nonsuper@test.local', "
                + "'Operador NonSuper', true) ON CONFLICT DO NOTHING");
        }
        // Estado REAL de uma rota de plataforma (console): sem tenant na sessão,
        // alcance irrestrito, ator conhecido. É exatamente o estado em que os
        // bugs de 28/jul aconteciam.
        TenantContext.clear();
        TenantContext.setUnrestricted(true);
        TenantContext.setUsuarioId(UUID.fromString("a4000000-0000-0000-0000-00000000ad01"));
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection c = superConnection(); Statement st = c.createStatement()) {
            for (String t : new String[]{"cliente", "modelo", "membro", "tenant_access"}) {
                st.execute("DELETE FROM " + t + " WHERE tenant_id = '" + TENANT + "'");
            }
            st.execute("UPDATE assinatura SET status = 'expirada' WHERE tenant_id = '"
                + TENANT + "' AND status = 'ativa'");
        }
        TenantContext.clear();
    }

    private long countSuper(String sql) throws SQLException {
        try (Connection c = superConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Espera a trilha assíncrona pousar (listener @Async, sem Awaitility no pom). */
    private long aguardarAuditoria(String acao, int segundos) throws Exception {
        String sql = "SELECT count(*) FROM auditoria WHERE acao = '" + acao
            + "' AND tenant_id = '" + TENANT + "' AND created_at > now() - interval '2 minutes'";
        for (int i = 0; i < segundos * 10; i++) {
            long n = countSuper(sql);
            if (n > 0) {
                return n;
            }
            Thread.sleep(100);
        }
        return 0;
    }

    // ------------------------------------------------------------------
    // Testes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sanidade: o contexto conecta sem superuser e sem BYPASSRLS")
    void datasourceNaoEhSuperuser() {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo(APP_ROLE);
        Boolean rolsuper = jdbc.queryForObject(
            "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user",
            Boolean.class);
        assertThat(rolsuper).isFalse();
    }

    @Test
    @DisplayName("reset via rota de plataforma: apaga sob RLS e a trilha assíncrona pousa")
    void resetSemTenantNaSessao() throws Exception {
        var r = resetService.reset(TENANT, Nivel.OPERACIONAL, SLUG);

        assertThat(r.apagados()).containsKey("cliente");
        assertThat(countSuper("SELECT count(*) FROM cliente WHERE tenant_id = '" + TENANT + "'"))
            .isZero();
        // Regressão de 28/jul: a linha de auditoria era BLOQUEADA pela RLS
        // (sessão sem tenant) e se perdia em silêncio — só o log de erro async
        // denunciava. Se este assert falhar, o fixarRlsDaLinha regrediu.
        assertThat(aguardarAuditoria("TENANT_RESET", 10))
            .as("trilha TENANT_RESET deve pousar mesmo sem tenant na sessão")
            .isPositive();
    }

    @Test
    @DisplayName("mudarPlano via rota de plataforma: sem 500 de ''::uuid, com trilha")
    void mudarPlanoSemTenantNaSessao() throws Exception {
        Integer trialId = jdbc.queryForObject(
            "SELECT id FROM plano WHERE nome = 'Trial'", Integer.class);

        // Regressão de 28/jul: sem setTenant() a policy da assinatura avaliava
        // ''::uuid e o primeiro mudarPlano vindo do console estourou 500.
        faturaService.mudarPlano(TENANT, trialId);

        assertThat(countSuper("SELECT count(*) FROM assinatura WHERE tenant_id = '" + TENANT
            + "' AND status = 'ativa' AND dt_fim IS NOT NULL")).isEqualTo(1);
        assertThat(aguardarAuditoria("TENANT_PLANO_ALTERADO", 10))
            .as("troca de plano audita (não só loga)")
            .isPositive();
    }

    @Test
    @DisplayName("round-trip export→reset→import inteiro sob RLS (WITH CHECK + setval)")
    void importRoundTripSobRls() throws Exception {
        var reset = resetService.reset(TENANT, Nivel.TOTAL, SLUG);

        var r = importService.importar(TENANT, reset.exportKey(), SLUG, false);

        assertThat(r.inseridos()).containsKeys("modelo", "cliente");
        assertThat(countSuper("SELECT count(*) FROM cliente WHERE tenant_id = '" + TENANT + "'"))
            .isEqualTo(1);
        assertThat(aguardarAuditoria("TENANT_IMPORT", 10)).isPositive();
    }
}
