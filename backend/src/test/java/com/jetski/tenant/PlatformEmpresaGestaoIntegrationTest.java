package com.jetski.tenant;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Console da plataforma: editar o cadastro e gerir os usuários de uma empresa.
 *
 * <p>Empresa própria da classe (não mexe no seed ACME). A matriz papel × ação está no
 * {@code platform_test.rego}; aqui o OPA é mockado e o foco são as regras de negócio.
 * O comportamento sob RLS real fica no {@code PlatformNonSuperuserIntegrationTest}.
 */
@AutoConfigureMockMvc
@DisplayName("Console: cadastro e usuários da empresa")
class PlatformEmpresaGestaoIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @MockBean OPAAuthorizationService opaAuthorizationService;
    @MockBean EmailService emailService;

    private static final UUID TENANT = UUID.fromString("c4d00000-0000-0000-0000-0000000000e1");
    private static final String SLUG = "gestao-console-teste";
    private static final UUID OPERADOR_PLATAFORMA = UUID.fromString("c4d00000-0000-0000-0000-00000000f001");
    private static final UUID ADMIN_A = UUID.fromString("c4d00000-0000-0000-0000-00000000a001");
    private static final UUID ADMIN_B = UUID.fromString("c4d00000-0000-0000-0000-00000000a002");
    private static final UUID OPERADOR = UUID.fromString("c4d00000-0000-0000-0000-00000000a003");

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO tenant (id, slug, razao_social, status) VALUES (?, ?, 'Gestão Console Ltda', 'ATIVO') "
            + "ON CONFLICT (id) DO NOTHING", TENANT, SLUG);
        jdbc.update("UPDATE tenant SET razao_social = 'Gestão Console Ltda', cnpj = NULL, uf = NULL, cidade = NULL, "
            + "whatsapp = NULL, telefone = NULL, responsavel_nome = NULL, email_oficial = NULL WHERE id = ?", TENANT);
        jdbc.update("DELETE FROM convite WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM tenant_access WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM membro WHERE tenant_id = ?", TENANT);

        seedUsuario(OPERADOR_PLATAFORMA, "operador.gestao@test.local", "Operador Gestão");
        jdbc.update("INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id, linked_at) "
            + "VALUES (?, 'keycloak', ?, NOW()) ON CONFLICT DO NOTHING", OPERADOR_PLATAFORMA, OPERADOR_PLATAFORMA.toString());
        jdbc.update("INSERT INTO usuario_global_roles (usuario_id, roles, unrestricted_access, created_at, updated_at) "
            + "VALUES (?, ARRAY['PLATFORM_SUPORTE'], TRUE, NOW(), NOW()) "
            + "ON CONFLICT (usuario_id) DO UPDATE SET unrestricted_access = TRUE", OPERADOR_PLATAFORMA);

        seedUsuario(ADMIN_A, "admin.a.gestao@test.local", "Admin A");
        seedUsuario(ADMIN_B, "admin.b.gestao@test.local", "Admin B");
        seedUsuario(OPERADOR, "operador.a.gestao@test.local", "Operador A");

        when(opaAuthorizationService.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());
    }

    private void seedUsuario(UUID id, String email, String nome) {
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo, email_verified) VALUES (?, ?, ?, TRUE, TRUE) "
            + "ON CONFLICT (id) DO NOTHING", id, email, nome);
    }

    private void seedMembro(UUID usuarioId, String papel, boolean ativo) {
        jdbc.update("INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) VALUES (?, ?, ?, ?)",
            TENANT, usuarioId, new String[]{papel}, ativo);
        jdbc.update("INSERT INTO tenant_access (usuario_id, tenant_id, roles, is_default) VALUES (?, ?, ?, FALSE) "
            + "ON CONFLICT DO NOTHING", usuarioId, TENANT, new String[]{papel});
    }

    private Boolean ativo(UUID usuarioId) {
        return jdbc.query("SELECT ativo FROM membro WHERE tenant_id = ? AND usuario_id = ?",
            rs -> rs.next() ? rs.getBoolean(1) : null, TENANT, usuarioId);
    }

    private RequestPostProcessor operador() {
        return jwt().jwt(j -> j.subject(OPERADOR_PLATAFORMA.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_SUPORTE"));
    }

    private static final String MOTIVO = "{\"motivo\": \"pedido da empresa por e-mail\"}";

    // ------------------------------------------------------------------ cadastro

    @Test
    @DisplayName("GET cadastro devolve slug e dados atuais")
    void verCadastro() throws Exception {
        mockMvc.perform(get("/v1/platform/tenants/{id}/cadastro", TENANT).with(operador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value(SLUG))
            .andExpect(jsonPath("$.razaoSocial").value("Gestão Console Ltda"));
    }

    @Test
    @DisplayName("PUT cadastro normaliza CNPJ/UF/WhatsApp e não toca no slug")
    void alterarCadastro() throws Exception {
        mockMvc.perform(put("/v1/platform/tenants/{id}/cadastro", TENANT).with(operador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"razaoSocial": "Gestão Console Náutica Ltda", "cnpj": "11222333000181",
                     "responsavelNome": "Maria", "telefone": "(48) 3333-4444", "whatsapp": "+55 (48) 99999-8888",
                     "emailOficial": "contato@gestao.test", "cidade": "Florianópolis", "uf": "sc",
                     "motivo": "correção pedida pela empresa"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.razaoSocial").value("Gestão Console Náutica Ltda"))
            .andExpect(jsonPath("$.cnpj").value("11.222.333/0001-81"))
            .andExpect(jsonPath("$.uf").value("SC"))
            .andExpect(jsonPath("$.whatsapp").value("5548999998888"))
            .andExpect(jsonPath("$.slug").value(SLUG));

        assertThat(jdbc.queryForObject("SELECT slug FROM tenant WHERE id = ?", String.class, TENANT)).isEqualTo(SLUG);
    }

    @Test
    @DisplayName("PUT cadastro: CNPJ com dígito errado, UF inexistente e sem motivo dão 400")
    void validacoesDoCadastro() throws Exception {
        mockMvc.perform(put("/v1/platform/tenants/{id}/cadastro", TENANT).with(operador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"razaoSocial\": \"X Ltda\", \"cnpj\": \"11.222.333/0001-00\", \"motivo\": \"m\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("CNPJ inválido")));

        mockMvc.perform(put("/v1/platform/tenants/{id}/cadastro", TENANT).with(operador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"razaoSocial\": \"X Ltda\", \"uf\": \"XX\", \"motivo\": \"m\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("UF inválida")));

        mockMvc.perform(put("/v1/platform/tenants/{id}/cadastro", TENANT).with(operador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"razaoSocial\": \"X Ltda\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("motivo")));

        assertThat(jdbc.queryForObject("SELECT razao_social FROM tenant WHERE id = ?", String.class, TENANT))
            .isEqualTo("Gestão Console Ltda");
    }

    @Test
    @DisplayName("OPA negando: PUT cadastro dá 403")
    void cadastroNegadoPeloOpa() throws Exception {
        when(opaAuthorizationService.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(false).tenantIsValid(true).build());

        mockMvc.perform(put("/v1/platform/tenants/{id}/cadastro", TENANT).with(operador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"razaoSocial\": \"X Ltda\", \"motivo\": \"m\"}"))
            .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ membros

    @Test
    @DisplayName("desativar e reativar um administrador quando há outro ativo")
    void desativarEReativar() throws Exception {
        seedMembro(ADMIN_A, "ADMIN_TENANT", true);
        seedMembro(ADMIN_B, "ADMIN_TENANT", true);

        mockMvc.perform(post("/v1/platform/tenants/{id}/membros/{u}/desativar", TENANT, ADMIN_A).with(operador())
                .contentType(MediaType.APPLICATION_JSON).content(MOTIVO))
            .andExpect(status().isNoContent());
        assertThat(ativo(ADMIN_A)).isFalse();

        mockMvc.perform(post("/v1/platform/tenants/{id}/membros/{u}/reativar", TENANT, ADMIN_A).with(operador())
                .contentType(MediaType.APPLICATION_JSON).content(MOTIVO))
            .andExpect(status().isNoContent());
        assertThat(ativo(ADMIN_A)).isTrue();
    }

    @Test
    @DisplayName("não desativa nem remove o último administrador ativo")
    void ultimoAdministrador() throws Exception {
        seedMembro(ADMIN_A, "ADMIN_TENANT", true);

        mockMvc.perform(post("/v1/platform/tenants/{id}/membros/{u}/desativar", TENANT, ADMIN_A).with(operador())
                .contentType(MediaType.APPLICATION_JSON).content(MOTIVO))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/platform/tenants/{id}/membros/{u}/remover", TENANT, ADMIN_A).with(operador())
                .contentType(MediaType.APPLICATION_JSON).content(MOTIVO))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("último administrador")));

        assertThat(ativo(ADMIN_A)).isTrue();
    }

    @Test
    @DisplayName("remover apaga só o vínculo com a empresa — a conta da pessoa continua")
    void removerMantemAConta() throws Exception {
        seedMembro(ADMIN_A, "ADMIN_TENANT", true);
        seedMembro(OPERADOR, "OPERADOR", true);

        mockMvc.perform(post("/v1/platform/tenants/{id}/membros/{u}/remover", TENANT, OPERADOR).with(operador())
                .contentType(MediaType.APPLICATION_JSON).content(MOTIVO))
            .andExpect(status().isNoContent());

        assertThat(ativo(OPERADOR)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tenant_access WHERE tenant_id = ? AND usuario_id = ?",
            Integer.class, TENANT, OPERADOR)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE id = ?", Integer.class, OPERADOR))
            .isEqualTo(1);
    }

    @Test
    @DisplayName("escrita sem motivo dá 400 e não altera nada")
    void semMotivo() throws Exception {
        seedMembro(ADMIN_A, "ADMIN_TENANT", true);
        seedMembro(OPERADOR, "OPERADOR", true);

        mockMvc.perform(post("/v1/platform/tenants/{id}/membros/{u}/desativar", TENANT, OPERADOR).with(operador())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        assertThat(ativo(OPERADOR)).isTrue();
    }

    @Test
    @DisplayName("convidar, listar e cancelar convite — convite registra o operador como autor")
    void convidarListarCancelar() throws Exception {
        seedMembro(ADMIN_A, "ADMIN_TENANT", true);

        mockMvc.perform(post("/v1/platform/tenants/{id}/membros/convites", TENANT).with(operador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "nova.pessoa@gestao.test", "nome": "Nova Pessoa",
                     "papeis": ["GERENTE"], "motivo": "empresa pediu novo gerente"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conviteId").exists())
            .andExpect(jsonPath("$.email").value("nova.pessoa@gestao.test"));

        UUID conviteId = jdbc.queryForObject("SELECT id FROM convite WHERE tenant_id = ? AND email = ?",
            UUID.class, TENANT, "nova.pessoa@gestao.test");
        assertThat(jdbc.queryForObject("SELECT created_by FROM convite WHERE id = ?", UUID.class, conviteId))
            .isEqualTo(OPERADOR_PLATAFORMA);

        mockMvc.perform(get("/v1/platform/tenants/{id}/membros/convites", TENANT).with(operador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].email").value("nova.pessoa@gestao.test"))
            .andExpect(jsonPath("$[0].status").value("PENDING"));

        mockMvc.perform(post("/v1/platform/tenants/{id}/membros/convites/{c}/cancelar", TENANT, conviteId)
                .with(operador()).contentType(MediaType.APPLICATION_JSON).content(MOTIVO))
            .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM convite WHERE id = ?", String.class, conviteId))
            .isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("convite com papel inválido dá 400")
    void convitePapelInvalido() throws Exception {
        mockMvc.perform(post("/v1/platform/tenants/{id}/membros/convites", TENANT).with(operador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"x@gestao.test\", \"nome\": \"X Y\", \"papeis\": [\"DONO\"], \"motivo\": \"m\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("Papel inválido")));
    }
}
