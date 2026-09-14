package com.jetski.locacoes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.integration.MembroDeTeste;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Link único para o instrutor assinar remotamente (V071).
 *
 * <p>Geração pela empresa (staff com papel real — {@link MembroDeTeste}) e página pública
 * sem autenticação. O comportamento sob RLS real está no
 * {@code InstrutorLinkNonSuperuserIntegrationTest}.
 */
@AutoConfigureMockMvc
@DisplayName("Link único de assinatura do instrutor")
class InstrutorAssinaturaLinkIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @MockBean OPAAuthorizationService opaAuthorizationService;

    private static final UUID TENANT = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID INSTRUTOR = UUID.fromString("1a570000-0000-4000-8000-00000000a551");
    /** PNG 1x1 válido, como o SignaturePad envia (dataURL). */
    private static final String PNG = "data:image/png;base64,"
        + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    @BeforeEach
    void setUp() {
        jdbc.update("""
            INSERT INTO instrutor (id, tenant_id, nome, ativo) VALUES (?, ?, 'Kemily Link', true)
            ON CONFLICT (id) DO UPDATE SET ativo = true, assinatura_s3_key = NULL, nome = 'Kemily Link'
            """, INSTRUTOR, TENANT);
        jdbc.update("DELETE FROM instrutor_assinatura_link WHERE instrutor_id = ?", INSTRUTOR);
        when(opaAuthorizationService.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());
    }

    /** Gera o link como GERENTE e devolve o token em claro (tirado da URL). */
    private String gerarToken() throws Exception {
        String body = mockMvc.perform(post("/v1/tenants/{t}/instrutores/{i}/link-assinatura", TENANT, INSTRUTOR)
                .header("X-Tenant-Id", TENANT.toString())
                .with(MembroDeTeste.comPapel(jdbc, TENANT, "GERENTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value(containsString("/assinar-instrutor?token=")))
            .andExpect(jsonPath("$.expiraEm").exists())
            .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        String url = json.get("url").asText();
        return url.substring(url.indexOf("token=") + "token=".length());
    }

    private String assinar(String token) {
        return "{\"assinaturaBase64\": \"" + PNG + "\"}";
    }

    @Test
    @DisplayName("fluxo completo: gerar → abrir → assinar grava a assinatura e as evidências")
    void fluxoCompleto() throws Exception {
        String token = gerarToken();

        mockMvc.perform(get("/v1/public/instrutor-assinatura/{t}", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.instrutorNome").value("Kemily Link"))
            .andExpect(jsonPath("$.empresa").exists())
            .andExpect(jsonPath("$.temAssinatura").value(false));

        mockMvc.perform(post("/v1/public/instrutor-assinatura/{t}", token)
                .contentType(MediaType.APPLICATION_JSON).content(assinar(token))
                .header("User-Agent", "JUnit-Celular"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.instrutorNome").value("Kemily Link"));

        assertThat(jdbc.queryForObject("SELECT assinatura_s3_key FROM instrutor WHERE id = ?", String.class, INSTRUTOR))
            .isNotNull();
        var link = jdbc.queryForMap("SELECT token_hash, usado_em, ativo, ip, user_agent, assinatura_sha256 "
            + "FROM instrutor_assinatura_link WHERE instrutor_id = ?", INSTRUTOR);
        assertThat(link.get("usado_em")).isNotNull();
        assertThat(link.get("ativo")).isEqualTo(false);
        assertThat(link.get("user_agent")).isEqualTo("JUnit-Celular");
        assertThat((String) link.get("assinatura_sha256")).hasSize(64);
        assertThat(link.get("token_hash")).as("só o hash vai para o banco").isNotEqualTo(token);
    }

    @Test
    @DisplayName("uso único: depois de assinar, abrir ou assinar de novo dá 410")
    void usoUnico() throws Exception {
        String token = gerarToken();
        mockMvc.perform(post("/v1/public/instrutor-assinatura/{t}", token)
                .contentType(MediaType.APPLICATION_JSON).content(assinar(token)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/v1/public/instrutor-assinatura/{t}", token)
                .contentType(MediaType.APPLICATION_JSON).content(assinar(token)))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.message").value(containsString("já foi usado")));
        mockMvc.perform(get("/v1/public/instrutor-assinatura/{t}", token))
            .andExpect(status().isGone());
    }

    @Test
    @DisplayName("gerar um link novo invalida o anterior")
    void novoLinkInvalidaAnterior() throws Exception {
        String antigo = gerarToken();
        String novo = gerarToken();

        mockMvc.perform(get("/v1/public/instrutor-assinatura/{t}", antigo))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.message").value(containsString("substituído")));
        mockMvc.perform(get("/v1/public/instrutor-assinatura/{t}", novo))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("link expirado dá 410")
    void expirado() throws Exception {
        String token = gerarToken();
        jdbc.update("UPDATE instrutor_assinatura_link SET expira_em = now() - interval '1 hour' WHERE instrutor_id = ?",
            INSTRUTOR);

        mockMvc.perform(get("/v1/public/instrutor-assinatura/{t}", token))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.message").value(containsString("expirou")));
    }

    @Test
    @DisplayName("token desconhecido dá 404")
    void tokenInvalido() throws Exception {
        mockMvc.perform(get("/v1/public/instrutor-assinatura/{t}", "A".repeat(40)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/public/instrutor-assinatura/{t}", "curto"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("imagem que não é PNG dá 400 e não consome o link")
    void imagemInvalida() throws Exception {
        String token = gerarToken();

        mockMvc.perform(post("/v1/public/instrutor-assinatura/{t}", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assinaturaBase64\": \"data:image/png;base64,SGVsbG8gbXVuZG8=\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/v1/public/instrutor-assinatura/{t}", token))
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT assinatura_s3_key FROM instrutor WHERE id = ?", String.class, INSTRUTOR))
            .isNull();
    }

    @Test
    @DisplayName("OPERADOR não gera link (403)")
    void operadorNaoGera() throws Exception {
        mockMvc.perform(post("/v1/tenants/{t}/instrutores/{i}/link-assinatura", TENANT, INSTRUTOR)
                .header("X-Tenant-Id", TENANT.toString())
                .with(MembroDeTeste.comPapel(jdbc, TENANT, "OPERADOR")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("instrutor inativo não recebe link (400)")
    void instrutorInativo() throws Exception {
        jdbc.update("UPDATE instrutor SET ativo = false WHERE id = ?", INSTRUTOR);

        mockMvc.perform(post("/v1/tenants/{t}/instrutores/{i}/link-assinatura", TENANT, INSTRUTOR)
                .header("X-Tenant-Id", TENANT.toString())
                .with(MembroDeTeste.comPapel(jdbc, TENANT, "GERENTE")))
            .andExpect(status().isBadRequest());
    }
}
