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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
 * Backlog P4: notificações in-app (sinal confirmado gera notificação; caixa
 * agrega vínculos; marcar lida com posse) + recibo PDF da locação finalizada.
 */
@AutoConfigureMockMvc
@DisplayName("Portal do Cliente — Notificações + Recibo")
class CustomerNotificacaoIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @MockBean OPAAuthorizationService opa;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID MODELO_ID = UUID.fromString("77777777-7777-4777-8777-000000000051");
    private static final UUID JETSKI_ID = UUID.fromString("77777777-7777-4777-8777-000000000052");
    private static final UUID CLIENTE_ID = UUID.fromString("77777777-7777-4777-8777-000000000053");
    private static final UUID RESERVA_ID = UUID.fromString("77777777-7777-4777-8777-000000000054");
    private static final UUID LOCACAO_ID = UUID.fromString("77777777-7777-4777-8777-000000000055");
    private static final String SUB = "efefefef-0000-0000-0000-000000000001";

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());

        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'Notif Modelo', 'Sea-Doo', 130, 2, 150.00, 5, 50.00, 300.00, FALSE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, MODELO_ID, TENANT_ACME);
        jdbc.update("""
            INSERT INTO jetski (id, tenant_id, modelo_id, serie, ano, horimetro_atual, status, ativo)
            VALUES (?, ?, ?, 'JET-NOTIF-1', 2024, 5.0, 'DISPONIVEL', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, JETSKI_ID, TENANT_ACME, MODELO_ID);

        jdbc.update("DELETE FROM cliente_notificacao WHERE tenant_id = ?", TENANT_ACME);
        jdbc.update("DELETE FROM locacao WHERE id = ?", LOCACAO_ID);
        jdbc.update("DELETE FROM reserva WHERE id = ?", RESERVA_ID);
        jdbc.update("DELETE FROM cliente_identity_provider WHERE provider_user_id = ?", SUB);
        jdbc.update("DELETE FROM cliente WHERE id = ?", CLIENTE_ID);

        jdbc.update("""
            INSERT INTO cliente (id, tenant_id, nome, email, documento, origem, status_conta, ativo)
            VALUES (?, ?, 'Cliente Notif', 'notif@test.com', '390.533.447-05', 'PORTAL', 'ATIVA', TRUE)
            """, CLIENTE_ID, TENANT_ACME);
        jdbc.update("""
            INSERT INTO cliente_identity_provider (tenant_id, cliente_id, provider, provider_user_id)
            VALUES (?, ?, 'keycloak', ?)
            """, TENANT_ACME, CLIENTE_ID, SUB);
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista,
                                 status, canal, sinal_pago, pagamento_status, pagamento_tipo,
                                 pagamento_valor_informado, ativo)
            VALUES (?, ?, ?, ?, now() + interval '2 days', now() + interval '2 days 2 hours',
                    'PENDENTE', 'PORTAL', false, 'EM_ANALISE', 'SINAL', 90.00, true)
            """, RESERVA_ID, TENANT_ACME, MODELO_ID, CLIENTE_ID);
    }

    private RequestPostProcessor cliente() {
        return jwt().jwt(j -> j.subject(SUB).claim("email", "notif@test.com"))
            .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    /** Usuário staff seedado (mesmo padrão do CustomerReservaIntegrationTest). */
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private RequestPostProcessor staff() {
        return jwt().jwt(j -> j.subject(USER_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_GERENTE"));
    }

    @Test
    @DisplayName("Confirmar sinal gera notificação; cliente lê e marca como lida")
    void testFluxoNotificacao() throws Exception {
        // staff confirma o pagamento do sinal
        mockMvc.perform(post("/v1/tenants/{t}/reservas/{id}/confirmar-sinal", TENANT_ACME, RESERVA_ID)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"valorSinal\": 90.00, \"tipo\": \"SINAL\"}")
                .with(staff()))
            .andExpect(status().isOk());

        // cliente vê a notificação na caixa
        MvcResult res = mockMvc.perform(get("/v1/customers/notificacoes").with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.naoLidas").value(1))
            .andExpect(jsonPath("$.itens[0].tipo").value("PAGAMENTO_CONFIRMADO"))
            .andExpect(jsonPath("$.itens[0].titulo",
                org.hamcrest.Matchers.containsString("confirmado")))
            .andExpect(jsonPath("$.itens[0].link",
                org.hamcrest.Matchers.containsString("/conta/reservas/")))
            .andReturn();

        String id = com.jayway.jsonpath.JsonPath.read(
            res.getResponse().getContentAsString(), "$.itens[0].id");

        // outro cliente não consegue marcar como lida
        mockMvc.perform(post("/v1/customers/notificacoes/{id}/lida", id)
                .with(jwt().jwt(j -> j.subject("outro-sub"))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
            .andExpect(status().isNotFound());

        // dono marca como lida → contagem zera
        mockMvc.perform(post("/v1/customers/notificacoes/{id}/lida", id).with(cliente()))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/customers/notificacoes").with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.naoLidas").value(0));
    }

    @Test
    @DisplayName("Recibo PDF: locação finalizada baixa; em curso é bloqueada")
    void testRecibo() throws Exception {
        jdbc.update("""
            INSERT INTO locacao (id, tenant_id, cliente_id, jetski_id, data_check_in, horimetro_inicio,
                                 duracao_prevista, data_check_out, horimetro_fim, minutos_usados,
                                 minutos_faturaveis, valor_base, valor_total, status)
            VALUES (?, ?, ?, ?, now() - interval '3 hours', 5.0,
                    120, now() - interval '1 hour', 7.0, 120, 115, 300.00, 320.00, 'FINALIZADA')
            """, LOCACAO_ID, TENANT_ACME, CLIENTE_ID, JETSKI_ID);

        MvcResult res = mockMvc.perform(get("/v1/customers/locacoes/{id}/recibo", LOCACAO_ID)
                .with(cliente()))
            .andExpect(status().isOk())
            .andExpect(result -> assertThat(result.getResponse().getContentType())
                .isEqualTo("application/pdf"))
            .andReturn();
        byte[] pdf = res.getResponse().getContentAsByteArray();
        assertThat(pdf.length).isGreaterThan(500);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        // em curso → 400
        jdbc.update("UPDATE locacao SET status = 'EM_CURSO', data_check_out = NULL WHERE id = ?", LOCACAO_ID);
        mockMvc.perform(get("/v1/customers/locacoes/{id}/recibo", LOCACAO_ID).with(cliente()))
            .andExpect(status().isBadRequest());
    }
}
