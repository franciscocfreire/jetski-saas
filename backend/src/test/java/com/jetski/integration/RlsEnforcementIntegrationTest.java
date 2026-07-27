package com.jetski.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Enforcement REAL de Row Level Security, exercitado por um role
 * <strong>não-superuser</strong> (via {@code SET LOCAL ROLE}).
 *
 * <p>Os demais testes de integração conectam como o superuser do Testcontainers
 * ({@code test}), que <em>bypassa</em> RLS — então o isolamento por tenant nunca
 * é validado de fato. Aqui, ao assumir um role comum (sem BYPASSRLS e que não é
 * dono das tabelas), as policies {@code tenant_isolation_*} passam a valer,
 * reproduzindo o cenário de produção (datasource conecta como {@code jetski_app}).
 *
 * <p>A policy do cliente é {@code USING (tenant_id = get_current_tenant_id())}
 * sem {@code WITH CHECK}; nesse caso o Postgres usa o {@code USING} também como
 * verificação de INSERT — por isso testamos leitura E escrita cross-tenant.
 */
@DisplayName("Integration: enforcement de RLS (role não-superuser)")
class RlsEnforcementIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private static final UUID TENANT_A = UUID.fromString("a1100000-0000-0000-0000-0000000000a1");
    private static final UUID TENANT_B = UUID.fromString("b2200000-0000-0000-0000-0000000000b2");
    private static final String ROLE = "rls_tester";

    @BeforeEach
    void setUp() {
        // Role comum: NOLOGIN (assumido via SET ROLE), NOBYPASSRLS (default).
        jdbc.execute("""
            DO $$ BEGIN
              IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'rls_tester') THEN
                CREATE ROLE rls_tester NOLOGIN;
              END IF;
            END $$;
            """);
        jdbc.execute("GRANT USAGE ON SCHEMA public TO rls_tester");
        jdbc.execute("GRANT SELECT, INSERT ON public.cliente TO rls_tester");
        jdbc.execute("GRANT SELECT ON public.cliente_claim_token TO rls_tester");

        // Tenants (FK de cliente.tenant_id) — superuser, idempotente.
        seedTenant(TENANT_A, "rls-tenant-a");
        seedTenant(TENANT_B, "rls-tenant-b");

        // Seed como superuser (RLS bypassada na inserção). IDs únicos por tenant.
        seed("a1c10000-0000-0000-0000-000000000001", TENANT_A, "A-Um");
        seed("a1c10000-0000-0000-0000-000000000002", TENANT_A, "A-Dois");
        seed("b2c10000-0000-0000-0000-000000000001", TENANT_B, "B-Um");

        // claim-token do tenant A (para o carve-out público do V009)
        jdbc.update("""
            INSERT INTO cliente_claim_token (tenant_id, cliente_id, token, temporary_password_hash, expira_em)
            VALUES (?, ?::uuid, 'TOKEN-RLS-CARVEOUT', 'hash', now() + interval '7 days')
            ON CONFLICT (token) DO NOTHING
            """, TENANT_A, "a1c10000-0000-0000-0000-000000000001");
    }

    private void seedTenant(UUID id, String slug) {
        jdbc.update("""
            INSERT INTO tenant (id, slug, razao_social) VALUES (?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """, id, slug, "RLS Test " + slug);
    }

    private void seed(String id, UUID tenant, String nome) {
        jdbc.update("""
            INSERT INTO cliente (id, tenant_id, nome) VALUES (?::uuid, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """, id, tenant, nome);
    }

    private Connection openAsRole() throws SQLException {
        Connection c = DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.execute("SET LOCAL ROLE " + ROLE);   // dropa o superuser → RLS passa a valer
        }
        return c;
    }

    private void setTenant(Connection c, String tenant) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.tenant_id', '" + tenant + "', true)");
        }
    }

    private List<String> nomesVisiveis(Connection c) throws SQLException {
        List<String> nomes = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT nome FROM cliente WHERE nome IN ('A-Um','A-Dois','B-Um') ORDER BY nome")) {
            while (rs.next()) nomes.add(rs.getString(1));
        }
        return nomes;
    }

    @Test
    @DisplayName("SELECT só enxerga o tenant do contexto; sem contexto, nada")
    void selectIsolaPorTenant() throws SQLException {
        try (Connection c = openAsRole()) {
            setTenant(c, TENANT_A.toString());
            assertThat(nomesVisiveis(c)).containsExactly("A-Dois", "A-Um");

            setTenant(c, TENANT_B.toString());
            assertThat(nomesVisiveis(c)).containsExactly("B-Um");

            // Sem app.tenant_id → get_current_tenant_id() = NULL → 0 linhas
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.tenant_id', '', true)");
            }
            assertThat(nomesVisiveis(c)).isEmpty();

            c.rollback();
        }
    }

    @Test
    @DisplayName("INSERT cross-tenant é bloqueado pela policy (USING como WITH CHECK)")
    void insertCrossTenantBloqueado() throws SQLException {
        try (Connection c = openAsRole()) {
            setTenant(c, TENANT_A.toString());
            assertThatThrownBy(() -> {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate(
                        "INSERT INTO cliente (tenant_id, nome) VALUES ('" + TENANT_B + "', 'intruso')");
                }
            }).isInstanceOf(SQLException.class)
              .hasMessageContaining("row-level security");
            c.rollback();
        }
    }

    @Test
    @DisplayName("INSERT no próprio tenant é permitido")
    void insertMesmoTenantOk() throws SQLException {
        try (Connection c = openAsRole()) {
            setTenant(c, TENANT_A.toString());
            int n;
            try (Statement st = c.createStatement()) {
                n = st.executeUpdate(
                    "INSERT INTO cliente (tenant_id, nome) VALUES ('" + TENANT_A + "', 'A-Tres')");
            }
            assertThat(n).isEqualTo(1);
            c.rollback();   // não polui outros testes
        }
    }

    @Test
    @DisplayName("Carve-out V009: token é legível sem tenant (público), mas cliente não")
    void claimTokenCarveOutSemTenant() throws SQLException {
        try (Connection c = openAsRole()) {
            // sem app.tenant_id: o claim-token É visível (carve-out p/ validação pública)
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT tenant_id FROM cliente_claim_token WHERE token = 'TOKEN-RLS-CARVEOUT'")) {
                assertThat(rs.next()).as("token legível sem tenant").isTrue();
                assertThat(rs.getString(1)).isEqualTo(TENANT_A.toString());
            }
            // ...mas o cliente (policy estrita) permanece invisível sem contexto
            assertThat(nomesVisiveis(c)).isEmpty();

            // com o tenant do token fixado, o cliente passa a ser visível
            setTenant(c, TENANT_A.toString());
            assertThat(nomesVisiveis(c)).contains("A-Um");
            c.rollback();
        }
    }

    /**
     * V057 abriu a leitura da trilha GLOBAL para o console. É a policy mais perigosa do
     * projeto: policies permissivas somam com OR, então se ela vazasse a condição de
     * {@code tenant_id IS NULL}, o operador de plataforma passaria a ler auditoria de
     * qualquer empresa sem sessão de suporte — exatamente o que o modelo evita.
     */
    @Test
    @DisplayName("V057: unrestricted lê a trilha global e NÃO enxerga auditoria de empresa")
    void auditoriaGlobalNaoVazaLinhaDeEmpresa() throws SQLException {
        jdbc.update("""
            INSERT INTO auditoria (tenant_id, acao, entidade)
            VALUES (NULL, 'RLS_TESTE_GLOBAL', 'teste'), (?, 'RLS_TESTE_TENANT', 'teste')
            """, TENANT_A);
        jdbc.execute("GRANT SELECT ON public.auditoria TO rls_tester");

        try (Connection c = openAsRole()) {
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.unrestricted', 'true', true)");
            }
            assertThat(acoesVisiveis(c))
                .as("operador de plataforma lê a trilha global")
                .containsExactly("RLS_TESTE_GLOBAL");

            // Com um tenant fixado, a policy de isolamento soma — mas o que ela acrescenta
            // é só o tenant do contexto, nunca "toda a auditoria".
            setTenant(c, TENANT_A.toString());
            assertThat(acoesVisiveis(c))
                .containsExactlyInAnyOrder("RLS_TESTE_GLOBAL", "RLS_TESTE_TENANT");

            // Sem a GUC, a trilha global some — não é leitura de graça.
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.unrestricted', '', true)");
                st.execute("SELECT set_config('app.tenant_id', '', true)");
            }
            assertThat(acoesVisiveis(c)).isEmpty();

            c.rollback();
        } finally {
            jdbc.update("DELETE FROM auditoria WHERE acao IN ('RLS_TESTE_GLOBAL','RLS_TESTE_TENANT')");
        }
    }

    /**
     * O audit DUAL da sessão de suporte grava duas linhas — uma na empresa e uma global — e
     * elas <strong>não cabem na mesma transação</strong>: a rota de plataforma não tem
     * tenant no contexto, então a linha da empresa viola a RLS e derruba as duas. Foi
     * exatamente o que aconteceu com {@code SUPORTE_SESSAO_ABERTA}, que nunca chegou ao
     * banco (descoberto ao montar a tela de auditoria da F5).
     *
     * <p>Os testes de integração da sessão de suporte não pegam isso: conectam como
     * superuser, que bypassa RLS. Este pega.
     */
    @Test
    @DisplayName("Auditoria de empresa exige o tenant no contexto; a global, não")
    void auditoriaExigeContextoDoProprioTenant() throws SQLException {
        jdbc.execute("GRANT SELECT, INSERT ON public.auditoria TO rls_tester");

        try (Connection c = openAsRole()) {
            // Sem tenant no contexto (é o estado de /v1/platform/**): a linha da empresa
            // é rejeitada — e numa transação única levaria a global junto.
            assertThatThrownBy(() -> inserirAuditoria(c, "'" + TENANT_A + "'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("row-level security");
            c.rollback();

            // A global passa sem tenant nenhum: é o que permite ao console lê-la depois.
            assertThat(inserirAuditoria(c, "NULL")).isEqualTo(1);
            c.rollback();

            // Com o tenant fixado, a linha da empresa passa — daí uma transação por linha.
            setTenant(c, TENANT_A.toString());
            assertThat(inserirAuditoria(c, "'" + TENANT_A + "'")).isEqualTo(1);
            c.rollback();
        }
    }

    private int inserirAuditoria(Connection c, String tenantSql) throws SQLException {
        try (Statement st = c.createStatement()) {
            return st.executeUpdate(
                "INSERT INTO auditoria (tenant_id, acao, entidade) VALUES ("
                + tenantSql + ", 'RLS_TESTE_GLOBAL', 'SESSAO_SUPORTE')");
        }
    }

    private List<String> acoesVisiveis(Connection c) throws SQLException {
        List<String> acoes = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT acao FROM auditoria WHERE acao LIKE 'RLS_TESTE_%' ORDER BY acao")) {
            while (rs.next()) acoes.add(rs.getString(1));
        }
        return acoes;
    }

    @Test
    @DisplayName("Controle: o superuser do container realmente bypassa RLS (todos os tenants)")
    void superuserBypassaRls() throws SQLException {
        try (Connection c = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            // como superuser, sem app.tenant_id, vê linhas de A e B
            assertThat(nomesVisiveis(c)).contains("A-Um", "A-Dois", "B-Um");
        }
    }
}
