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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Devolutiva da Marinha (staff): anexar a resposta manual da Marinha confirma
 * a CHA-MTA-E — auditada, notificada ao cliente e baixável no portal.
 */
@AutoConfigureMockMvc
@DisplayName("Habilitação — Devolutiva da Marinha (CHA-MTA-E confirmada)")
class HabilitacaoDevolutivaIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @MockBean OPAAuthorizationService opa;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID MODELO_ACME = UUID.fromString("77777777-7777-4777-8777-000000000061");
    private static final String SUB = "dededede-0000-0000-0000-000000000001";
    private static final String GRU = "60893100243858888";
    private static final String PDF_B64 = Base64.getEncoder()
        .encodeToString("%PDF-1.4 fake devolutiva".getBytes(StandardCharsets.UTF_8));

    private UUID clienteId;
    private UUID reservaId;

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());

        jdbc.update("UPDATE tenant SET status = 'ATIVO' WHERE id = ?", TENANT_ACME);
        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'Devolutiva Modelo', 'Sea-Doo', 130, 2, 150.00, 5, 50.00, 300.00, FALSE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, MODELO_ACME, TENANT_ACME);

        jdbc.update("DELETE FROM cliente_notificacao WHERE tenant_id = ? AND tipo = 'CHA_CONFIRMADA'", TENANT_ACME);
        jdbc.update("DELETE FROM reserva_habilitacao WHERE reserva_id IN " +
            "(SELECT id FROM reserva WHERE cliente_id IN (SELECT id FROM cliente WHERE email = 'devolutiva@test.com'))");
        jdbc.update("DELETE FROM reserva WHERE cliente_id IN (SELECT id FROM cliente WHERE email = 'devolutiva@test.com')");
        jdbc.update("DELETE FROM cliente_identity_provider WHERE provider_user_id = ?", SUB);
        jdbc.update("DELETE FROM cliente WHERE email = 'devolutiva@test.com'");

        clienteId = UUID.randomUUID();
        reservaId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO cliente (id, tenant_id, nome, email, origem, status_conta, ativo)
            VALUES (?, ?, 'Cliente Devolutiva', 'devolutiva@test.com', 'PORTAL', 'ATIVA', TRUE)
            """, clienteId, TENANT_ACME);
        jdbc.update("""
            INSERT INTO cliente_identity_provider (tenant_id, cliente_id, provider, provider_user_id)
            VALUES (?, ?, 'keycloak', ?)
            """, TENANT_ACME, clienteId, SUB);
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista,
                                 status, canal, documento_emitido_em)
            VALUES (?, ?, ?, ?, now() - interval '1 day', now() - interval '1 day' + interval '2 hours',
                    'CONFIRMADA', 'PORTAL', now() - interval '1 day')
            """, reservaId, TENANT_ACME, MODELO_ACME, clienteId);
        jdbc.update("""
            INSERT INTO reserva_habilitacao (tenant_id, reserva_id, via, gru_numero, gru_pago, resolvida)
            VALUES (?, ?, 'EMA', ?, TRUE, TRUE)
            """, TENANT_ACME, reservaId, GRU);
    }

    /** Sub precisa existir como usuário (TenantFilter resolve keycloak→usuario). */
    private static final UUID STAFF_USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private RequestPostProcessor staff() {
        return jwt().jwt(j -> j.subject(STAFF_USER.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_GERENTE"));
    }

    private RequestPostProcessor cliente() {
        return jwt().jwt(j -> j.subject(SUB)
                .claim("name", "Cliente Devolutiva")
                .claim("email", "devolutiva@test.com")
                .claim("email_verified", true))
            .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private String base() {
        return "/v1/tenants/" + TENANT_ACME + "/reservas/" + reservaId + "/habilitacao";
    }

    @Test
    @DisplayName("PUT devolutiva confirma a CHA-MTA-E — auditada + notificação (sem duplicar no re-upload)")
    void testAnexarDevolutiva() throws Exception {
        mockMvc.perform(put(base() + "/devolutiva")
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudoBase64\":\"" + PDF_B64 + "\"}")
                .with(staff()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.marinhaConfirmadaEm").isNotEmpty())
            .andExpect(jsonPath("$.devolutivaDisponivel").value(true));

        var row = jdbc.queryForMap(
            "SELECT marinha_confirmada_em, cha_mtae_s3_key FROM reserva_habilitacao WHERE reserva_id = ?",
            reservaId);
        assertThat(row.get("marinha_confirmada_em")).isNotNull();
        assertThat((String) row.get("cha_mtae_s3_key")).contains("cha-mtae-confirmada.pdf");

        // notificação ao cliente (sininho)
        Integer notifs = jdbc.queryForObject(
            "SELECT count(*) FROM cliente_notificacao WHERE cliente_id = ? AND tipo = 'CHA_CONFIRMADA'",
            Integer.class, clienteId);
        assertThat(notifs).isEqualTo(1);

        // auditoria assíncrona (poll limitado)
        Integer audits = 0;
        for (int i = 0; i < 50 && audits == 0; i++) {
            audits = jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE acao = 'CHA_MTAE_CONFIRMADA' AND entidade_id = ?",
                Integer.class, reservaId);
            Thread.sleep(100);
        }
        assertThat(audits).isGreaterThanOrEqualTo(1);

        // re-upload substitui SEM nova notificação, preservando a confirmação original
        var confirmadaOriginal = row.get("marinha_confirmada_em");
        mockMvc.perform(put(base() + "/devolutiva")
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudoBase64\":\"" + PDF_B64 + "\"}")
                .with(staff()))
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM cliente_notificacao WHERE cliente_id = ? AND tipo = 'CHA_CONFIRMADA'",
            Integer.class, clienteId)).isEqualTo(1);
        assertThat(jdbc.queryForMap(
            "SELECT marinha_confirmada_em FROM reserva_habilitacao WHERE reserva_id = ?", reservaId)
            .get("marinha_confirmada_em")).isEqualTo(confirmadaOriginal);
    }

    @Test
    @DisplayName("Guards: via CHA → 400; GRU não paga → 400; download staff/cliente 200 e 404")
    void testGuardsEDownload() throws Exception {
        // download antes de anexar → 404 (staff e cliente)
        mockMvc.perform(get(base() + "/devolutiva/download")
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff()))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/customers/habilitacoes/{id}/documento", reservaId).with(cliente()))
            .andExpect(status().isNotFound());

        // GRU não paga → 400
        jdbc.update("UPDATE reserva_habilitacao SET gru_pago = FALSE WHERE reserva_id = ?", reservaId);
        mockMvc.perform(put(base() + "/devolutiva")
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudoBase64\":\"" + PDF_B64 + "\"}")
                .with(staff()))
            .andExpect(status().isBadRequest());

        // via CHA → 400
        jdbc.update("UPDATE reserva_habilitacao SET via = 'CHA', gru_pago = TRUE WHERE reserva_id = ?", reservaId);
        mockMvc.perform(put(base() + "/devolutiva")
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudoBase64\":\"" + PDF_B64 + "\"}")
                .with(staff()))
            .andExpect(status().isBadRequest());

        // anexa de verdade e baixa nos dois escopos
        jdbc.update("UPDATE reserva_habilitacao SET via = 'EMA' WHERE reserva_id = ?", reservaId);
        mockMvc.perform(put(base() + "/devolutiva")
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudoBase64\":\"" + PDF_B64 + "\"}")
                .with(staff()))
            .andExpect(status().isOk());

        mockMvc.perform(get(base() + "/devolutiva/download")
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"));
        mockMvc.perform(get("/v1/customers/habilitacoes/{id}/documento", reservaId).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"));
    }
}
