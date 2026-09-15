package com.jetski.integration;

import com.jetski.shared.security.TenantContext;
import com.jetski.signup.api.dto.CreateTenantRequest;
import com.jetski.signup.internal.TenantSignupService;
import com.jetski.tenant.internal.PlatformFaturaService;
import com.jetski.tenant.internal.PlatformTenantService;
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
@DisplayName("Plataforma sob role não-superuser (RLS valendo de verdade)")
class PlatformNonSuperuserIntegrationTest extends AbstractNonSuperuserIntegrationTest {

    private static final UUID TENANT = UUID.fromString("a4000000-0000-0000-0000-0000000000aa");
    private static final String SLUG = "nonsuper-teste";

    @Autowired private TenantResetService resetService;
    @Autowired private TenantImportService importService;
    @Autowired private PlatformFaturaService faturaService;
    @Autowired private PlatformTenantService platformTenantService;
    @Autowired private com.jetski.tenant.internal.CondicaoComercialService condicaoService;
    @Autowired private TenantSignupService tenantSignupService;
    @Autowired private com.jetski.usuarios.internal.PlatformMembroService platformMembroService;
    @Autowired private com.jetski.tenant.internal.PlatformCadastroService platformCadastroService;
    @Autowired private JdbcTemplate jdbc; // conecta como app_test

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
            for (String t : new String[]{"cliente", "modelo", "membro", "tenant_access", "condicao_comercial"}) {
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

    // ---------------------------------------------- console: usuários e cadastro

    private static final UUID MEMBRO_ADMIN = UUID.fromString("a4000000-0000-0000-0000-00000000be01");
    private static final UUID MEMBRO_OPERADOR = UUID.fromString("a4000000-0000-0000-0000-00000000be02");

    private void seedMembros(boolean operadorAtivo) throws SQLException {
        try (Connection c = superConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO usuario (id, email, nome, ativo) VALUES ('" + MEMBRO_ADMIN
                + "', 'nonsuper.admin@test.local', 'Admin NonSuper', true) ON CONFLICT DO NOTHING");
            st.execute("INSERT INTO usuario (id, email, nome, ativo) VALUES ('" + MEMBRO_OPERADOR
                + "', 'nonsuper.operador@test.local', 'Operador NonSuper', true) ON CONFLICT DO NOTHING");
            st.execute("INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) VALUES ('" + TENANT + "', '"
                + MEMBRO_ADMIN + "', ARRAY['ADMIN_TENANT'], true)");
            st.execute("INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) VALUES ('" + TENANT + "', '"
                + MEMBRO_OPERADOR + "', ARRAY['OPERADOR'], " + operadorAtivo + ")");
        }
    }

    @Test
    @DisplayName("console: reativar membro lê o limite do plano sob RLS e a trilha pousa")
    void reativarMembroSemTenantNaSessao() throws Exception {
        seedMembros(false);

        // Sem fixar a empresa, o PlanoLimiteService lia assinatura com ''::uuid → 500.
        platformMembroService.reativar(TENANT, MEMBRO_OPERADOR, "pedido da empresa");

        assertThat(countSuper("SELECT count(*) FROM membro WHERE tenant_id = '" + TENANT
            + "' AND usuario_id = '" + MEMBRO_OPERADOR + "' AND ativo")).isEqualTo(1);
        assertThat(aguardarAuditoria("TENANT_MEMBRO_REATIVADO", 10)).isPositive();
    }

    @Test
    @DisplayName("console: remover membro sob RLS apaga só o vínculo e audita")
    void removerMembroSemTenantNaSessao() throws Exception {
        seedMembros(true);

        platformMembroService.remover(TENANT, MEMBRO_OPERADOR, "saiu da empresa");

        assertThat(countSuper("SELECT count(*) FROM membro WHERE tenant_id = '" + TENANT
            + "' AND usuario_id = '" + MEMBRO_OPERADOR + "'")).isZero();
        assertThat(countSuper("SELECT count(*) FROM usuario WHERE id = '" + MEMBRO_OPERADOR + "'")).isEqualTo(1);
        assertThat(aguardarAuditoria("TENANT_MEMBRO_REMOVIDO", 10)).isPositive();
    }

    @Test
    @DisplayName("console: convidar sob RLS grava o convite e as duas trilhas pousam")
    void convidarSemTenantNaSessao() throws Exception {
        seedMembros(true);
        try {
            platformMembroService.convidar(TENANT, "nonsuper.convidado@test.local", "Convidado NonSuper",
                java.util.List.of("GERENTE"), "novo gerente");

            assertThat(countSuper("SELECT count(*) FROM convite WHERE tenant_id = '" + TENANT
                + "' AND email = 'nonsuper.convidado@test.local' AND status = 'PENDING'")).isEqualTo(1);
            assertThat(aguardarAuditoria("TENANT_MEMBRO_CONVIDADO", 10)).isPositive();
            // Evento da própria regra de convite: sem fixarRlsDaLinha no listener se perdia em silêncio
            assertThat(aguardarAuditoria("MEMBER_INVITED", 10)).isPositive();
        } finally {
            try (Connection c = superConnection(); Statement st = c.createStatement()) {
                st.execute("DELETE FROM convite WHERE tenant_id = '" + TENANT + "'");
            }
        }
    }

    @Test
    @DisplayName("console: alterar cadastro da empresa sob RLS audita a diferença")
    void alterarCadastroSemTenantNaSessao() throws Exception {
        platformCadastroService.alterar(TENANT, new com.jetski.tenant.internal.PlatformCadastroService.AlteracaoCadastro(
            "NonSuper Teste Náutica Ltda", "11.222.333/0001-81", null, null, null, null, "Itajaí", "SC",
            "correção cadastral"));

        assertThat(countSuper("SELECT count(*) FROM tenant WHERE id = '" + TENANT
            + "' AND cnpj = '11.222.333/0001-81' AND uf = 'SC'")).isEqualTo(1);
        assertThat(aguardarAuditoria("TENANT_CADASTRO_ALTERADO", 10)).isPositive();
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
    @DisplayName("V076: conceder condição comercial sob RLS — grava, aparece na listagem e audita")
    void condicaoComercialSemTenantNaSessao() throws Exception {
        condicaoService.conceder(TENANT, new com.jetski.tenant.internal.CondicaoComercialService.NovaCondicao(
            com.jetski.tenant.internal.CondicaoComercialService.Tipo.PILOTO,
            com.jetski.tenant.internal.CondicaoComercialService.Forma.ISENCAO, null, null,
            java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusMonths(2),
            "piloto sob RLS"));

        assertThat(countSuper("SELECT count(*) FROM condicao_comercial WHERE tenant_id = '"
            + TENANT + "' AND forma = 'ISENCAO'")).isEqualTo(1);
        // A listagem do console lê a condição empresa a empresa: sem fixar o tenant, a
        // policy esconderia a linha e a empresa apareceria como "paga cheio".
        assertThat(platformTenantService.listAll())
            .filteredOn(s -> s.id().equals(TENANT.toString()))
            .singleElement()
            .satisfies(s -> assertThat(s.condicao()).isNotNull());
        assertThat(aguardarAuditoria("TENANT_CONDICAO_COMERCIAL_CONCEDIDA", 10)).isPositive();
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

    /**
     * V069 fechou o INSERT {@code WITH CHECK (true)} de assinatura: a aprovação (rota de
     * plataforma, sem tenant na sessão) precisa fixar o tenant da linha na transação.
     */
    @Test
    @DisplayName("V069: aprovar empresa cria a assinatura Trial sob RLS (sem tenant na sessão)")
    void aprovarCriaTrialSobRls() throws Exception {
        UUID pendente = UUID.randomUUID();
        try (Connection c = superConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO tenant (id, slug, razao_social, status) VALUES ('" + pendente
                + "', 'v069-aprova-" + pendente.toString().substring(0, 8)
                + "', 'V069 Aprovação Ltda', 'PENDENTE_APROVACAO')");
        }
        try {
            platformTenantService.approve(pendente);

            assertThat(countSuper("SELECT count(*) FROM assinatura WHERE tenant_id = '" + pendente
                + "' AND status = 'ativa' AND dt_fim IS NOT NULL")).isEqualTo(1);
        } finally {
            removerTenant(pendente);
        }
    }

    /**
     * V069 fechou o INSERT {@code WITH CHECK (true)} de fuel_policy: o cadastro de empresa
     * (pessoa autenticada sem empresa corrente = sem tenant na sessão) fixa o tenant novo
     * só para a política padrão de combustível.
     */
    @Test
    @DisplayName("V069: cadastrar empresa cria a fuel_policy padrão sob RLS (sem tenant na sessão)")
    void cadastrarEmpresaCriaFuelPolicySobRls() throws Exception {
        TenantContext.clear(); // estado da rota /v1/tenants/create: pessoa sem empresa corrente
        String slug = "v069-cadastro-" + UUID.randomUUID().toString().substring(0, 8);

        var resp = tenantSignupService.createTenantForExistingUser(
            new CreateTenantRequest("V069 Cadastro Ltda", slug, null),
            UUID.fromString("a4000000-0000-0000-0000-00000000ad01"));

        try {
            assertThat(countSuper("SELECT count(*) FROM fuel_policy WHERE tenant_id = '"
                + resp.tenantId() + "' AND tipo = 'INCLUSO'")).isEqualTo(1);
            assertThat(countSuper("SELECT count(*) FROM membro WHERE tenant_id = '"
                + resp.tenantId() + "'")).isEqualTo(1);
        } finally {
            removerTenant(resp.tenantId());
        }
    }

    private void removerTenant(UUID tenantId) throws SQLException {
        try (Connection c = superConnection(); Statement st = c.createStatement()) {
            for (String t : new String[]{"fuel_policy", "assinatura", "membro", "tenant_access", "auditoria"}) {
                st.execute("DELETE FROM " + t + " WHERE tenant_id = '" + tenantId + "'");
            }
            st.execute("DELETE FROM tenant WHERE id = '" + tenantId + "'");
        } catch (SQLException e) {
            // FK de alguma trilha assíncrona tardia: o tenant fica, com slug único — não
            // interfere nos demais testes.
        }
    }
}
