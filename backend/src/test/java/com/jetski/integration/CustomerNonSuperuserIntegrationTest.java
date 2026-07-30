package com.jetski.integration;

import com.jetski.locacoes.internal.CustomerAccountService;
import com.jetski.locacoes.internal.CustomerProfileService;
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
 * Escopo customer sob role não-superuser (identidade única, F1 —
 * IDENTIDADE_UNICA_SPEC §5/F1): a resolução multi-loja nova
 * (sub → usuario → fichas via policy {@code cliente_self_read}) é exercitada
 * com a RLS VALENDO — a policy é exatamente o tipo de código que a suíte
 * superuser aprovaria mesmo quebrado. Cobre também a propagação de identidade
 * com o {@code saveAndFlush} por tenant (gotcha flush×RLS, até hoje só
 * coberto com RLS bypassada). O fallback legado (V029) morreu na F4.
 */
@DisplayName("Escopo customer sob role não-superuser (cliente_self_read + fallback)")
class CustomerNonSuperuserIntegrationTest extends AbstractNonSuperuserIntegrationTest {

    private static final UUID TENANT_UM = UUID.fromString("f1000000-0000-0000-0000-0000000000aa");
    private static final UUID TENANT_DOIS = UUID.fromString("f1000000-0000-0000-0000-0000000000bb");
    private static final UUID PESSOA_A = UUID.fromString("f1000000-0000-0000-0000-000000000a01");
    private static final UUID PESSOA_B = UUID.fromString("f1000000-0000-0000-0000-000000000b01");
    private static final UUID FICHA_A1 = UUID.fromString("f1000000-0000-0000-0000-00000000ca01");
    private static final UUID FICHA_A2 = UUID.fromString("f1000000-0000-0000-0000-00000000ca02");
    private static final UUID FICHA_B1 = UUID.fromString("f1000000-0000-0000-0000-00000000cb01");

    @Autowired private CustomerAccountService accountService;
    @Autowired private CustomerProfileService profileService;

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection c = superConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO tenant (id, slug, razao_social, status) VALUES "
                + "('" + TENANT_UM + "', 'f1-loja-um', 'F1 Loja Um', 'ATIVO'), "
                + "('" + TENANT_DOIS + "', 'f1-loja-dois', 'F1 Loja Dois', 'ATIVO') "
                + "ON CONFLICT DO NOTHING");
            // Pessoas (caminho novo): usuario + mapping global
            st.execute("INSERT INTO usuario (id, email, nome, ativo) VALUES "
                + "('" + PESSOA_A + "', 'pessoa-a@f1.test', 'Pessoa A', true), "
                + "('" + PESSOA_B + "', 'pessoa-b@f1.test', 'Pessoa B', true) "
                + "ON CONFLICT DO NOTHING");
            st.execute("INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id) VALUES "
                + "('" + PESSOA_A + "', 'keycloak', 'sub-f1-a'), "
                + "('" + PESSOA_B + "', 'keycloak', 'sub-f1-b') "
                + "ON CONFLICT DO NOTHING");
            // Fichas pós-F0: SÓ cliente.usuario_id (sem tabela de vínculo legada)
            st.execute("INSERT INTO cliente (id, tenant_id, nome, usuario_id, ativo, origem, status_conta) VALUES "
                + "('" + FICHA_A1 + "', '" + TENANT_UM + "', 'A na Um', '" + PESSOA_A + "', true, 'PORTAL', 'ATIVA'), "
                + "('" + FICHA_A2 + "', '" + TENANT_DOIS + "', 'A na Dois', '" + PESSOA_A + "', true, 'PORTAL', 'ATIVA'), "
                + "('" + FICHA_B1 + "', '" + TENANT_UM + "', 'B na Um', '" + PESSOA_B + "', true, 'PORTAL', 'ATIVA') "
                + "ON CONFLICT DO NOTHING");
        }
        // Estado real do escopo customer: sem tenant, sem unrestricted
        TenantContext.clear();
        TenantContext.setUserRoles(java.util.List.of("CLIENTE"));
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection c = superConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM cliente WHERE tenant_id IN ('" + TENANT_UM + "', '" + TENANT_DOIS + "')");
            st.execute("DELETE FROM customer_profile WHERE usuario_id IN ('" + PESSOA_A + "', '" + PESSOA_B + "')");
            st.execute("DELETE FROM usuario_identity_provider WHERE provider_user_id LIKE 'sub-f1-%'");
            st.execute("DELETE FROM auditoria WHERE usuario_id IN ('" + PESSOA_A + "', '" + PESSOA_B + "')");
            st.execute("DELETE FROM usuario WHERE email LIKE '%@f1.test'");
        }
        TenantContext.clear();
    }

    private String querySuper(String sql) throws SQLException {
        try (Connection c = superConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    @DisplayName("caminho novo: pessoa resolve as PRÓPRIAS fichas em todas as lojas")
    void resolucaoMultiLojaPelaPessoa() {
        var deA = accountService.vinculos("sub-f1-a");
        var deB = accountService.vinculos("sub-f1-b");

        assertThat(deA).extracting("clienteId")
            .containsExactlyInAnyOrder(FICHA_A1, FICHA_A2);
        assertThat(deA).extracting("slug")
            .containsExactlyInAnyOrder("f1-loja-um", "f1-loja-dois");
        // Isolamento sob RLS real: B não enxerga NADA de A — se a policy
        // cliente_self_read vazasse (ou o GUC fosse ignorado), este assert cai
        assertThat(deB).extracting("clienteId").containsExactly(FICHA_B1);
    }

    @Test
    @DisplayName("F4: sub sem pessoa não resolve NADA (fallback legado morreu por design)")
    void subSemPessoaNaoResolve() {
        assertThat(accountService.vinculos("sub-f1-inexistente")).isEmpty();
    }

    @Test
    @DisplayName("exigirVinculo nega loja alheia e fixa o tenant da própria")
    void exigirVinculoSobRls() {
        var v = accountService.exigirVinculo("sub-f1-a", TENANT_DOIS);
        assertThat(v.getClienteId()).isEqualTo(FICHA_A2);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> accountService.exigirVinculo("sub-f1-b", TENANT_DOIS))
            .isInstanceOf(com.jetski.shared.exception.NotFoundException.class);
    }

    @Test
    @DisplayName("propagação de identidade: saveAndFlush por tenant sob RLS REAL")
    void propagacaoFlushRlsReal() throws Exception {
        // Perfil civil da pessoa A (global, sem RLS)
        try (Connection c = superConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO customer_profile (usuario_id, nome) "
                + "VALUES ('" + PESSOA_A + "', 'Pessoa A') "
                + "ON CONFLICT DO NOTHING");
        }

        profileService.atualizar("sub-f1-a", "Pessoa A", "pessoa-a@f1.test",
            new CustomerProfileService.AtualizarCmd(
                null, "RG-F1-999", "SSP", null, null, null, null));

        // O gotcha flush×RLS: sem o saveAndFlush POR tenant, o UPDATE da loja
        // anterior seria filtrado pela RLS do tenant seguinte e sumiria em
        // silêncio — só um harness não-superuser consegue provar o contrário.
        assertThat(querySuper("SELECT rg FROM cliente WHERE id = '" + FICHA_A1 + "'"))
            .isEqualTo("RG-F1-999");
        assertThat(querySuper("SELECT rg FROM cliente WHERE id = '" + FICHA_A2 + "'"))
            .isEqualTo("RG-F1-999");
        // E a ficha de B fica intacta (propagação não vaza entre pessoas)
        assertThat(querySuper("SELECT rg FROM cliente WHERE id = '" + FICHA_B1 + "'")).isNull();
    }
}
