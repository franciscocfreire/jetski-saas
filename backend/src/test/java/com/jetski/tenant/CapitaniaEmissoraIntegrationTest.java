package com.jetski.tenant;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.security.TenantAccessInfo;
import com.jetski.usuarios.internal.TenantAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fundação da emissão delegada (V047): catálogo de capitanias, perfil de
 * emissão do tenant (capitania + registro EAMA) e habilitação de emissora
 * pelo super admin — incluindo a queda da habilitação em alteração cadastral.
 */
@AutoConfigureMockMvc
@DisplayName("Capitania + perfil emissora (V047)")
class CapitaniaEmissoraIntegrationTest extends AbstractIntegrationTest {

    private static final UUID TENANT = UUID.fromString("a4700000-0000-0000-0000-0000000000aa");
    private static final String USER = "22222222-2222-2222-2222-222222222222";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private OPAAuthorizationService opaAuthorizationService;
    @MockBean private TenantAccessService tenantAccessService;

    private UUID capitaniaSp;
    private UUID capitaniaRj;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO tenant (id, slug, razao_social, status) "
            + "VALUES (?, 'emissora-teste', 'Emissora Teste Ltda', 'ATIVO') "
            + "ON CONFLICT (id) DO NOTHING", TENANT);
        jdbc.update("UPDATE tenant SET capitania_id = NULL, emissora_habilitada = false, "
            + "eama_registro = NULL, eama_registro_validade = NULL, marinha_email = NULL "
            + "WHERE id = ?", TENANT);
        capitaniaSp = jdbc.queryForObject(
            "SELECT id FROM capitania WHERE codigo = 'CPSP'", UUID.class);
        capitaniaRj = jdbc.queryForObject(
            "SELECT id FROM capitania WHERE codigo = 'CPRJ'", UUID.class);
        jdbc.update("UPDATE capitania SET email_oficial = NULL, ativa = true "
            + "WHERE codigo IN ('CPSP','CPRJ')");

        when(opaAuthorizationService.authorize(any())).thenReturn(
            OPADecision.builder().allow(true).tenantIsValid(true).build());
        when(tenantAccessService.validateAccess(any(String.class), any(String.class), any(UUID.class)))
            .thenReturn(TenantAccessInfo.builder()
                .hasAccess(true)
                .roles(List.of("ADMIN_TENANT"))
                .unrestricted(false)
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
    @DisplayName("GET /v1/capitanias lista o catálogo seed (só ativas)")
    void catalogoListaAtivas() throws Exception {
        jdbc.update("UPDATE capitania SET ativa = false WHERE codigo = 'CPRJ'");
        try {
            mockMvc.perform(get("/v1/capitanias")
                    .header("X-Tenant-Id", TENANT.toString())
                    .with(jwtAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo=='CPSP')]").exists())
                .andExpect(jsonPath("$[?(@.codigo=='CPRJ')]").doesNotExist());
        } finally {
            jdbc.update("UPDATE capitania SET ativa = true WHERE codigo = 'CPRJ'");
        }
    }

    @Test
    @DisplayName("perfil de emissão: declarar capitania+registro, pré-preencher marinha_email e habilitar pelo super admin")
    void fluxoDeclararEHabilitar() throws Exception {
        jdbc.update("UPDATE capitania SET email_oficial = 'cpsp@marinha.mil.br' WHERE codigo = 'CPSP'");

        // tenant declara capitania + registro EAMA
        mockMvc.perform(put("/v1/tenants/{t}/config/emissora", TENANT)
                .header("X-Tenant-Id", TENANT.toString())
                .contentType("application/json")
                .content("{\"capitaniaId\":\"" + capitaniaSp + "\",\"eamaRegistro\":\"EAMA-SP-123\","
                    + "\"eamaRegistroValidade\":\"2027-12-31\"}")
                .with(jwtAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capitaniaCodigo").value("CPSP"))
            .andExpect(jsonPath("$.eamaRegistro").value("EAMA-SP-123"))
            .andExpect(jsonPath("$.emissoraHabilitada").value(false));

        // e-mail da capitania pré-preencheu o marinha_email (estava vazio)
        String marinhaEmail = jdbc.queryForObject(
            "SELECT marinha_email FROM tenant WHERE id = ?", String.class, TENANT);
        assertThat(marinhaEmail).isEqualTo("cpsp@marinha.mil.br");

        // super admin habilita
        mockMvc.perform(post("/v1/platform/tenants/{t}/habilitar-emissora", TENANT)
                .header("X-Tenant-Id", TENANT.toString())
                .with(jwtAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emissoraHabilitada").value(true));

        // alterar o cadastro (outra capitania) derruba a habilitação
        mockMvc.perform(put("/v1/tenants/{t}/config/emissora", TENANT)
                .header("X-Tenant-Id", TENANT.toString())
                .contentType("application/json")
                .content("{\"capitaniaId\":\"" + capitaniaRj + "\"}")
                .with(jwtAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capitaniaCodigo").value("CPRJ"))
            .andExpect(jsonPath("$.emissoraHabilitada").value(false));
    }

    @Test
    @DisplayName("habilitar emissora sem capitania/registro declarados nega com 400 de negócio")
    void habilitarSemCadastroNega() throws Exception {
        mockMvc.perform(post("/v1/platform/tenants/{t}/habilitar-emissora", TENANT)
                .header("X-Tenant-Id", TENANT.toString())
                .with(jwtAdmin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("capitania")));
    }

    @Test
    @DisplayName("capitania inativa não pode ser declarada pelo tenant")
    void capitaniaInativaNega() throws Exception {
        jdbc.update("UPDATE capitania SET ativa = false WHERE codigo = 'CPRJ'");
        try {
            mockMvc.perform(put("/v1/tenants/{t}/config/emissora", TENANT)
                    .header("X-Tenant-Id", TENANT.toString())
                    .contentType("application/json")
                    .content("{\"capitaniaId\":\"" + capitaniaRj + "\"}")
                    .with(jwtAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                    org.hamcrest.Matchers.containsString("inativa")));
        } finally {
            jdbc.update("UPDATE capitania SET ativa = true WHERE codigo = 'CPRJ'");
        }
    }
}
