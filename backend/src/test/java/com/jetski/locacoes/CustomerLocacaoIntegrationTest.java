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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P4 do Portal do Cliente: histórico de locações do cliente (multi-loja) e
 * avaliações (nota única por locação FINALIZADA) com média pública na vitrine.
 */
@AutoConfigureMockMvc
@DisplayName("Portal do Cliente — P4 (locações + avaliações)")
class CustomerLocacaoIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @MockBean OPAAuthorizationService opa;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID MODELO_ID = UUID.fromString("77777777-7777-4777-8777-000000000031");
    private static final UUID JETSKI_ID = UUID.fromString("77777777-7777-4777-8777-000000000032");
    private static final UUID CLIENTE_ID = UUID.fromString("77777777-7777-4777-8777-000000000033");
    private static final UUID LOCACAO_ID = UUID.fromString("77777777-7777-4777-8777-000000000034");
    private static final String SUB = "abababab-0000-0000-0000-000000000001";
    // Pessoa (identidade única, F4) — MESMO id no CustomerHabilitacaoIntegrationTest (mesmo SUB)
    private static final UUID USUARIO_ID = UUID.fromString("abababab-0000-0000-0000-0000000000aa");

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());

        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo, exibir_no_marketplace)
            VALUES (?, ?, 'P4 Modelo', 'Sea-Doo', 130, 2, 150.00, 5, 50.00, 300.00, FALSE, TRUE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, MODELO_ID, TENANT_ACME);
        jdbc.update("UPDATE modelo SET exibir_no_marketplace = true, ativo = true WHERE id = ?", MODELO_ID);
        jdbc.update("""
            INSERT INTO jetski (id, tenant_id, modelo_id, serie, ano, horimetro_atual, status, ativo)
            VALUES (?, ?, ?, 'JET-P4-1', 2024, 20.0, 'DISPONIVEL', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, JETSKI_ID, TENANT_ACME, MODELO_ID);

        jdbc.update("DELETE FROM avaliacao WHERE tenant_id = ?", TENANT_ACME);
        jdbc.update("DELETE FROM locacao WHERE id = ?", LOCACAO_ID);
        jdbc.update("DELETE FROM cliente WHERE id = ?", CLIENTE_ID);

        // cliente + vínculo + locação FINALIZADA
        jdbc.update("""
            INSERT INTO cliente (id, tenant_id, nome, email, origem, status_conta, ativo)
            VALUES (?, ?, 'Cliente P4', 'p4@test.com', 'PORTAL', 'ATIVA', TRUE)
            """, CLIENTE_ID, TENANT_ACME);
        vincularPessoa(CLIENTE_ID, SUB, USUARIO_ID, "p4@test.com", "Cliente P4");
        jdbc.update("""
            INSERT INTO locacao (id, tenant_id, cliente_id, jetski_id, data_check_in, horimetro_inicio,
                                 duracao_prevista, data_check_out, horimetro_fim, minutos_usados,
                                 minutos_faturaveis, valor_base, valor_total, status)
            VALUES (?, ?, ?, ?, now() - interval '3 hours', 10.0,
                    120, now() - interval '1 hour', 12.0, 120, 120, 300.00, 300.00, 'FINALIZADA')
            """, LOCACAO_ID, TENANT_ACME, CLIENTE_ID, JETSKI_ID);
    }


    /** Identidade única (F4): pessoa global + mapping do sub + ficha apontando à pessoa. */
    private void vincularPessoa(UUID clienteId, String sub, UUID usuarioId, String email, String nome) {
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo) VALUES (?, ?, ?, TRUE) ON CONFLICT DO NOTHING",
            usuarioId, email, nome);
        jdbc.update("""
            INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id)
            SELECT ?, 'keycloak', ?
            WHERE NOT EXISTS (SELECT 1 FROM usuario_identity_provider
                               WHERE provider = 'keycloak' AND provider_user_id = ?)
            """, usuarioId, sub, sub);
        UUID pessoa = jdbc.queryForObject(
            "SELECT usuario_id FROM usuario_identity_provider WHERE provider = 'keycloak' AND provider_user_id = ?",
            UUID.class, sub);
        // sobras de outras classes com o MESMO sub/tenant violariam o unique (tenant_id, usuario_id)
        jdbc.update("UPDATE cliente SET usuario_id = NULL WHERE usuario_id = ? "
            + "AND tenant_id = (SELECT tenant_id FROM cliente WHERE id = ?) AND id <> ?",
            pessoa, clienteId, clienteId);
        jdbc.update("UPDATE cliente SET usuario_id = ? WHERE id = ?", pessoa, clienteId);
    }

    private RequestPostProcessor cliente() {
        return jwt().jwt(j -> j.subject(SUB)
                .claim("name", "Cliente P4")
                .claim("email", "p4@test.com")
                .claim("email_verified", true))
            .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    @Test
    @DisplayName("Histórico lista a locação finalizada com valores e loja")
    void testHistorico() throws Exception {
        mockMvc.perform(get("/v1/customers/locacoes").with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(LOCACAO_ID.toString()))
            .andExpect(jsonPath("$[0].lojaSlug").value("acme"))
            .andExpect(jsonPath("$[0].modeloNome").value("P4 Modelo"))
            .andExpect(jsonPath("$[0].valorTotal").value(300.00))
            .andExpect(jsonPath("$[0].status").value("FINALIZADA"))
            .andExpect(jsonPath("$[0].avaliacaoNota").isEmpty());

        mockMvc.perform(get("/v1/customers/locacoes/{id}", LOCACAO_ID).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locacao.jetskiSerie").value("JET-P4-1"))
            .andExpect(jsonPath("$.fotos").isArray());

        // outro cliente não enxerga
        mockMvc.perform(get("/v1/customers/locacoes/{id}", LOCACAO_ID)
                .with(jwt().jwt(j -> j.subject("outro-sub"))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Avaliar: registra nota única e alimenta a média pública do marketplace")
    void testAvaliar() throws Exception {
        mockMvc.perform(post("/v1/customers/locacoes/{id}/avaliacao", LOCACAO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nota\":5,\"comentario\":\"Passeio incrível!\"}")
                .with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.avaliacaoNota").value(5))
            .andExpect(jsonPath("$.avaliacaoComentario").value("Passeio incrível!"));

        // segunda avaliação bloqueia
        mockMvc.perform(post("/v1/customers/locacoes/{id}/avaliacao", LOCACAO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nota\":1}")
                .with(cliente()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message",
                org.hamcrest.Matchers.containsString("já foi avaliada")));

        // média pública na vitrine (sem auth)
        mockMvc.perform(get("/v1/public/lojas/acme/modelos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '%s')].notaMedia".formatted(MODELO_ID),
                org.hamcrest.Matchers.contains(5.0)))
            .andExpect(jsonPath("$[?(@.id == '%s')].totalAvaliacoes".formatted(MODELO_ID),
                org.hamcrest.Matchers.contains(1)));
    }

    @Test
    @DisplayName("Locação EM_CURSO não pode ser avaliada")
    void testAvaliarEmCurso() throws Exception {
        jdbc.update("UPDATE locacao SET status = 'EM_CURSO', data_check_out = NULL WHERE id = ?", LOCACAO_ID);
        mockMvc.perform(post("/v1/customers/locacoes/{id}/avaliacao", LOCACAO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nota\":4}")
                .with(cliente()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message",
                org.hamcrest.Matchers.containsString("finalizada")));
    }

    @Test
    @DisplayName("Branding público da loja responde cores (white-label do portal)")
    void testBrandingPublico() throws Exception {
        jdbc.update("UPDATE tenant SET exibir_no_marketplace = true, " +
            "branding = '{\"cor_primaria\": \"#123456\", \"cor_secundaria\": \"#C9A24B\"}'::jsonb " +
            "WHERE id = ?", TENANT_ACME);

        mockMvc.perform(get("/v1/public/lojas/acme/branding"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.corPrimaria").value("#123456"));

        mockMvc.perform(get("/v1/public/lojas/nao-existe/branding"))
            .andExpect(status().isNotFound());
    }
}
