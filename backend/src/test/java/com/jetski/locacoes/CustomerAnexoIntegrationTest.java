package com.jetski.locacoes;

import com.jetski.integration.AbstractIntegrationTest;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Documentos do cliente pela página de PERFIL (por loja vinculada):
 * upload/list/preview com posse por vínculo — e trilha LGPD sem conteúdo.
 */
@AutoConfigureMockMvc
@DisplayName("Portal do Cliente — Documentos no perfil (por loja)")
class CustomerAnexoIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @MockBean OPAAuthorizationService opa;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID TENANT_MARINA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final String SUB = "efefefef-0000-0000-0000-000000000001";
    private static final String SUB2 = "efefefef-0000-0000-0000-000000000002";
    private static final String PNG_B64 = Base64.getEncoder().encodeToString(new byte[]{
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3});

    private UUID clienteId;

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());

        jdbc.update("DELETE FROM cliente_identity_provider WHERE provider_user_id IN (?, ?)", SUB, SUB2);
        jdbc.update("DELETE FROM cliente WHERE email = 'anexoperfil@test.com'");

        clienteId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO cliente (id, tenant_id, nome, email, origem, status_conta, ativo)
            VALUES (?, ?, 'Cliente Anexo', 'anexoperfil@test.com', 'PORTAL', 'ATIVA', TRUE)
            """, clienteId, TENANT_ACME);
        jdbc.update("""
            INSERT INTO cliente_identity_provider (tenant_id, cliente_id, provider, provider_user_id)
            VALUES (?, ?, 'keycloak', ?)
            """, TENANT_ACME, clienteId, SUB);
    }

    private RequestPostProcessor cliente(String sub) {
        return jwt().jwt(j -> j.subject(sub)
                .claim("name", "Cliente Anexo")
                .claim("email", "anexoperfil@test.com")
                .claim("email_verified", true))
            .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private String base(UUID tenantId) {
        return "/v1/customers/self/lojas/" + tenantId + "/anexos";
    }

    @Test
    @DisplayName("Upload → list → preview; tipo ausente 404; CHA 400")
    void testUploadListPreview() throws Exception {
        mockMvc.perform(post(base(TENANT_ACME))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tipo":"IDENTIDADE","conteudoBase64":"data:image/png;base64,%s"}
                    """.formatted(PNG_B64))
                .with(cliente(SUB)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem("IDENTIDADE")));

        mockMvc.perform(get(base(TENANT_ACME)).with(cliente(SUB)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem("IDENTIDADE")));

        mockMvc.perform(get(base(TENANT_ACME) + "/IDENTIDADE").with(cliente(SUB)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"));

        mockMvc.perform(get(base(TENANT_ACME) + "/SELFIE").with(cliente(SUB)))
            .andExpect(status().isNotFound());

        mockMvc.perform(post(base(TENANT_ACME))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tipo":"CHA","conteudoBase64":"data:image/png;base64,%s"}
                    """.formatted(PNG_B64))
                .with(cliente(SUB)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Posse: loja sem vínculo 404; outro sub não acessa")
    void testPosse() throws Exception {
        // SUB não tem vínculo com a marina
        mockMvc.perform(get(base(TENANT_MARINA)).with(cliente(SUB)))
            .andExpect(status().isNotFound());

        // SUB2 não tem vínculo com a ACME
        mockMvc.perform(get(base(TENANT_ACME)).with(cliente(SUB2)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Upload gera trilha LGPD (CLIENTE_ANEXO_ATUALIZADO, origem PORTAL, sem conteúdo)")
    void testAuditoriaAnexo() throws Exception {
        mockMvc.perform(post(base(TENANT_ACME))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tipo":"SELFIE","conteudoBase64":"data:image/png;base64,%s"}
                    """.formatted(PNG_B64))
                .with(cliente(SUB)))
            .andExpect(status().isOk());

        // handler é @Async — espera limitada
        Integer count = 0;
        for (int i = 0; i < 50 && count == 0; i++) {
            count = jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE acao = 'CLIENTE_ANEXO_ATUALIZADO' " +
                "AND entidade_id = ?", Integer.class, clienteId);
            Thread.sleep(100);
        }
        assertThat(count).isGreaterThanOrEqualTo(1);

        String dados = jdbc.queryForObject(
            "SELECT dados_novos::text FROM auditoria WHERE acao = 'CLIENTE_ANEXO_ATUALIZADO' " +
            "AND entidade_id = ? ORDER BY created_at DESC LIMIT 1", String.class, clienteId);
        assertThat(dados).contains("SELFIE").contains("PORTAL");
        assertThat(dados).doesNotContain("base64").doesNotContain(PNG_B64.substring(0, 8));
    }
}
