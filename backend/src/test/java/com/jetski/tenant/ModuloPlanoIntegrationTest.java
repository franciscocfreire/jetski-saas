package com.jetski.tenant;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantAccessInfo;
import com.jetski.marketplace.internal.MarketplaceService;
import com.jetski.usuarios.internal.TenantAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gating de módulos por plano (V046): leitura do jsonb (NULL = todos),
 * negação de negócio (400) no interceptor para path de módulo fora do
 * plano, bypass do superadmin e evict do cache na troca.
 */
@AutoConfigureMockMvc
@DisplayName("Módulos por plano (gating de oferta)")
class ModuloPlanoIntegrationTest extends AbstractIntegrationTest {

    private static final UUID TENANT = UUID.fromString("a4000000-0000-0000-0000-0000000000ee");
    private static final String USER = "11111111-1111-1111-1111-111111111111";

    @Autowired private MockMvc mockMvc;
    @Autowired private PlanoLimiteService planoLimiteService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CacheManager cacheManager;
    @Autowired private MarketplaceService marketplaceService;

    @MockBean private OPAAuthorizationService opaAuthorizationService;
    @MockBean private TenantAccessService tenantAccessService;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO tenant (id, slug, razao_social, status) "
            + "VALUES (?, 'modulos-teste', 'Modulos Teste Ltda', 'ATIVO') "
            + "ON CONFLICT (id) DO NOTHING", TENANT);
        jdbc.update("INSERT INTO plano (nome, preco_mensal, modulos) "
            + "VALUES ('Modulos Teste', 99, '[\"MANUTENCAO\",\"FECHAMENTOS\"]'::jsonb) "
            + "ON CONFLICT (nome) DO UPDATE SET modulos = EXCLUDED.modulos");
        jdbc.update("DELETE FROM assinatura WHERE tenant_id = ?", TENANT);
        jdbc.update("INSERT INTO assinatura (tenant_id, plano_id, ciclo, dt_inicio, status) "
            + "SELECT ?, id, 'mensal', CURRENT_DATE, 'ativa' FROM plano WHERE nome = 'Modulos Teste'",
            TENANT);
        limparCache();

        when(opaAuthorizationService.authorize(any())).thenReturn(
            OPADecision.builder().allow(true).tenantIsValid(true).build());
        mockAcesso(false);
    }

    private void limparCache() {
        var cache = cacheManager.getCache("plano-modulos");
        if (cache != null) {
            cache.clear();
        }
    }

    private void mockAcesso(boolean unrestricted) {
        when(tenantAccessService.validateAccess(any(String.class), any(String.class), any(UUID.class)))
            .thenReturn(TenantAccessInfo.builder()
                .hasAccess(true)
                .roles(List.of("ADMIN_TENANT"))
                .unrestricted(unrestricted)
                .build());
    }

    private static RequestPostProcessor jwtAdmin() {
        return jwt().jwt(j -> j
                .subject(USER)
                .claim("tenant_id", TENANT.toString())
                .claim("roles", List.of("ADMIN_TENANT")))
            .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_ADMIN_TENANT"));
    }

    @Test
    @DisplayName("lê os módulos do plano; fora da lista = não habilitado, com mensagem de upgrade")
    void leituraEVerificacao() {
        assertThat(planoLimiteService.modulosDoPlano(TENANT))
            .containsExactlyInAnyOrder("MANUTENCAO", "FECHAMENTOS");
        assertThat(planoLimiteService.moduloHabilitado(TENANT, ModuloPlano.MANUTENCAO)).isTrue();
        assertThat(planoLimiteService.moduloHabilitado(TENANT, ModuloPlano.DESPESAS)).isFalse();

        assertThatThrownBy(() -> planoLimiteService.verificarModulo(TENANT, ModuloPlano.DESPESAS))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Despesas operacionais")
            .hasMessageContaining("upgrade");
    }

    @Test
    @DisplayName("plano.modulos NULL = todos (sentinela *); sem assinatura também")
    void nullOuSemAssinaturaLiberaTudo() {
        jdbc.update("UPDATE plano SET modulos = NULL WHERE nome = 'Modulos Teste'");
        limparCache();
        assertThat(planoLimiteService.modulosDoPlano(TENANT)).containsExactly("*");
        assertThatCode(() -> planoLimiteService.verificarModulo(TENANT, ModuloPlano.DESPESAS))
            .doesNotThrowAnyException();

        jdbc.update("UPDATE assinatura SET status = 'expirada' WHERE tenant_id = ?", TENANT);
        jdbc.update("UPDATE plano SET modulos = '[\"MANUTENCAO\"]'::jsonb WHERE nome = 'Modulos Teste'");
        limparCache();
        assertThat(planoLimiteService.moduloHabilitado(TENANT, ModuloPlano.DESPESAS)).isTrue();
    }

    @Test
    @DisplayName("API de módulo fora do plano nega com 400 e mensagem de upgrade; módulo incluído passa")
    void interceptorNegaModuloForaDoPlano() throws Exception {
        mockMvc.perform(get("/v1/tenants/{t}/despesas-operacionais", TENANT)
                .header("X-Tenant-Id", TENANT.toString())
                .with(jwtAdmin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("não está incluído no seu plano")));

        mockMvc.perform(get("/v1/tenants/{t}/manutencoes", TENANT)
                .header("X-Tenant-Id", TENANT.toString())
                .with(jwtAdmin()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("superadmin (unrestricted) não é bloqueado pelo gating")
    void superadminBypass() throws Exception {
        mockAcesso(true);
        mockMvc.perform(get("/v1/tenants/{t}/despesas-operacionais", TENANT)
                .header("X-Tenant-Id", TENANT.toString())
                .param("dataInicio", "2026-07-01")
                .param("dataFim", "2026-07-31")
                .with(jwtAdmin()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("MARKETPLACE fora do plano: modelo some do marketplace agregado; NULL = aparece")
    void marketplaceGate() {
        UUID modeloId = UUID.fromString("a4000000-0000-0000-0000-0000000000ef");
        jdbc.update("INSERT INTO modelo (id, tenant_id, nome, preco_base_hora, ativo, exibir_no_marketplace) "
            + "VALUES (?, ?, 'Gate Teste', 100, true, true) ON CONFLICT (id) DO NOTHING",
            modeloId, TENANT);
        jdbc.update("UPDATE tenant SET exibir_no_marketplace = true WHERE id = ?", TENANT);

        // plano do setUp: MANUTENCAO+FECHAMENTOS (sem MARKETPLACE) → some
        assertThat(marketplaceService.listPublicModelos())
            .noneMatch(m -> m.id().equals(modeloId));
        assertThat(marketplaceService.getPublicModelo(modeloId)).isEmpty();

        // NULL = todos → aparece
        jdbc.update("UPDATE plano SET modulos = NULL WHERE nome = 'Modulos Teste'");
        limparCache();
        assertThat(marketplaceService.listPublicModelos())
            .anyMatch(m -> m.id().equals(modeloId));
        assertThat(marketplaceService.getPublicModelo(modeloId)).isPresent();
    }

    @Test
    @DisplayName("LOJA_ONLINE fora do plano: vitrine some, disponibilidade 404 e reserva online nega")
    void lojaOnlineGate() throws Exception {
        jdbc.update("UPDATE tenant SET exibir_no_marketplace = true WHERE id = ?", TENANT);

        // sem LOJA_ONLINE no plano do setUp
        assertThat(marketplaceService.getPublicLoja("modulos-teste")).isEmpty();
        assertThat(marketplaceService.listPublicModelosByLoja("modulos-teste")).isEmpty();
        mockMvc.perform(get("/v1/public/lojas/modulos-teste/disponibilidade")
                .param("modeloId", UUID.randomUUID().toString())
                .param("dataInicio", "2026-08-01T10:00:00")
                .param("dataFimPrevista", "2026-08-01T11:00:00"))
            .andExpect(status().isNotFound());

        // NULL = todos → vitrine volta
        jdbc.update("UPDATE plano SET modulos = NULL WHERE nome = 'Modulos Teste'");
        limparCache();
        assertThat(marketplaceService.getPublicLoja("modulos-teste")).isPresent();
    }

    @Test
    @DisplayName("emissão delegada: cobre documentos/grus mas NÃO instrutores; qualquer módulo de emissão no plano libera o path compartilhado")
    void splitEmissaoPropriaDelegada() throws Exception {
        // patterns: instrutores é exclusivo da emissão própria
        assertThat(ModuloPlano.EMISSAO_DELEGADA.cobre("documentos")).isTrue();
        assertThat(ModuloPlano.EMISSAO_DELEGADA.cobre("grus/123")).isTrue();
        assertThat(ModuloPlano.EMISSAO_DELEGADA.cobre(
            "reservas/" + UUID.randomUUID() + "/emitir-documentos")).isTrue();
        assertThat(ModuloPlano.EMISSAO_DELEGADA.cobre("instrutores")).isFalse();
        assertThat(ModuloPlano.EMISSAO_PROPRIA.cobre("instrutores")).isTrue();

        // interceptor: plano só com EMISSAO_DELEGADA → documentos passa (path coberto
        // pelos dois módulos de emissão, basta um), instrutores nega com 400
        jdbc.update("UPDATE plano SET modulos = '[\"EMISSAO_DELEGADA\"]'::jsonb "
            + "WHERE nome = 'Modulos Teste'");
        limparCache();
        mockMvc.perform(get("/v1/tenants/{t}/documentos", TENANT)
                .header("X-Tenant-Id", TENANT.toString())
                .with(jwtAdmin()))
            .andExpect(status().isOk());
        mockMvc.perform(get("/v1/tenants/{t}/instrutores", TENANT)
                .header("X-Tenant-Id", TENANT.toString())
                .with(jwtAdmin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("não está incluído no seu plano")));
    }

    @Test
    @DisplayName("padrões dos módulos cobrem os sub-paths certos e poupam o core")
    void padroesDosModulos() {
        assertThat(ModuloPlano.EMISSAO_PROPRIA.cobre("documentos")).isTrue();
        assertThat(ModuloPlano.EMISSAO_PROPRIA.cobre("grus/123")).isTrue();
        assertThat(ModuloPlano.EMISSAO_PROPRIA.cobre(
            "reservas/" + UUID.randomUUID() + "/habilitacao/gru")).isTrue();
        assertThat(ModuloPlano.COMISSOES.cobre("vendedores")).isTrue();
        assertThat(ModuloPlano.RELATORIOS.cobre("dashboard/financeiro")).isTrue();
        assertThat(ModuloPlano.RELATORIOS.cobre("dashboard")).isFalse();

        // core nunca gateado
        for (ModuloPlano m : ModuloPlano.values()) {
            assertThat(m.cobre("locacoes")).as("locacoes vs %s", m).isFalse();
            assertThat(m.cobre("reservas")).as("reservas vs %s", m).isFalse();
            assertThat(m.cobre("jetskis")).as("jetskis vs %s", m).isFalse();
            assertThat(m.cobre("clientes")).as("clientes vs %s", m).isFalse();
        }
    }
}
