package com.jetski.integration;

import com.jetski.locacoes.internal.InstrutorAssinaturaLinkService;
import com.jetski.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Link de assinatura do instrutor sob role NÃO-superuser (RLS valendo de verdade).
 *
 * <p>A página pública chega sem tenant na sessão e o {@code instrutor} tem RLS estrita
 * ({@code tenant_id = get_current_tenant_id()}): sem fixar a empresa do link na transação,
 * a leitura do instrutor não acha nada e a assinatura se perde. A suíte superuser não pega.
 */
@DisplayName("Link de assinatura do instrutor sob RLS real")
class InstrutorLinkNonSuperuserIntegrationTest extends AbstractNonSuperuserIntegrationTest {

    private static final UUID TENANT = UUID.fromString("a4100000-0000-0000-0000-0000000000aa");
    private static final UUID INSTRUTOR = UUID.fromString("a4100000-0000-0000-0000-00000000a551");
    private static final String PNG = "data:image/png;base64,"
        + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    @Autowired private InstrutorAssinaturaLinkService linkService;

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection c = superConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO tenant (id, slug, razao_social, status) VALUES ('" + TENANT
                + "', 'instrutor-link-rls', 'Instrutor Link RLS Ltda', 'ATIVO') ON CONFLICT DO NOTHING");
            st.execute("INSERT INTO instrutor (id, tenant_id, nome, ativo) VALUES ('" + INSTRUTOR + "', '" + TENANT
                + "', 'Instrutor RLS', true) ON CONFLICT (id) DO UPDATE SET ativo = true, assinatura_s3_key = NULL");
            st.execute("DELETE FROM instrutor_assinatura_link WHERE instrutor_id = '" + INSTRUTOR + "'");
        }
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private String superString(String sql) throws SQLException {
        try (Connection c = superConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    @Test
    @DisplayName("gerado pela empresa; aberto e assinado sem tenant na sessão")
    void assinaturaPublicaSobRls() throws Exception {
        // Empresa gera (rota autenticada: tenant na sessão)
        TenantContext.setTenantId(TENANT);
        String url = linkService.gerar(INSTRUTOR).url();
        String token = url.substring(url.indexOf("token=") + "token=".length());
        TenantContext.clear();

        // Instrutor abre e assina (rota pública: sem tenant)
        var info = linkService.consultar(token);
        assertThat(info.instrutorNome()).isEqualTo("Instrutor RLS");
        assertThat(info.empresa()).isEqualTo("Instrutor Link RLS Ltda");
        TenantContext.clear();

        linkService.assinar(token, PNG, "203.0.113.7", "JUnit");
        TenantContext.clear();

        assertThat(superString("SELECT assinatura_s3_key FROM instrutor WHERE id = '" + INSTRUTOR + "'"))
            .isNotNull();
        assertThat(superString("SELECT ip FROM instrutor_assinatura_link WHERE instrutor_id = '" + INSTRUTOR
            + "' AND usado_em IS NOT NULL")).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("com tenant de OUTRA empresa na sessão, a empresa não vê os links alheios")
    void outraEmpresaNaoVeLinks() throws Exception {
        TenantContext.setTenantId(TENANT);
        linkService.gerar(INSTRUTOR);
        TenantContext.clear();

        TenantContext.setTenantId(UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"));
        Long visiveis = jdbc().queryForObject(
            "SELECT count(*) FROM instrutor_assinatura_link WHERE instrutor_id = ?", Long.class, INSTRUTOR);
        assertThat(visiveis).isZero();
    }

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private org.springframework.jdbc.core.JdbcTemplate jdbc() {
        return jdbcTemplate;
    }
}
