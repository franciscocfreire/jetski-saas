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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ficha da reserva: JSON agregado (cliente/extrato/habilitação/ciclo) e
 * PDF via link temporário de uso único (PublicPdfController).
 */
@AutoConfigureMockMvc
@DisplayName("Reserva — Ficha (detalhe + PDF)")
class ReservaFichaIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @MockBean OPAAuthorizationService opa;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID MODELO_ACME = UUID.fromString("77777777-7777-4777-8777-000000000071");
    private static final UUID STAFF_USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private UUID clienteId;
    private UUID reservaId;

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());

        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'Ficha Modelo', 'Yamaha', 110, 2, 120.00, 5, 50.00, 300.00, FALSE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, MODELO_ACME, TENANT_ACME);

        jdbc.update("DELETE FROM reserva_lancamento WHERE reserva_id IN " +
            "(SELECT id FROM reserva WHERE cliente_id IN (SELECT id FROM cliente WHERE email = 'ficha@test.com'))");
        jdbc.update("DELETE FROM reserva_habilitacao WHERE reserva_id IN " +
            "(SELECT id FROM reserva WHERE cliente_id IN (SELECT id FROM cliente WHERE email = 'ficha@test.com'))");
        jdbc.update("DELETE FROM reserva WHERE cliente_id IN (SELECT id FROM cliente WHERE email = 'ficha@test.com')");
        jdbc.update("DELETE FROM cliente WHERE email = 'ficha@test.com'");

        clienteId = UUID.randomUUID();
        reservaId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO cliente (id, tenant_id, nome, email, documento, telefone,
                                 origem, status_conta, ativo)
            VALUES (?, ?, 'Cliente Ficha', 'ficha@test.com', '222.333.444-55', '48988887777',
                    'BALCAO', 'ATIVA', TRUE)
            """, clienteId, TENANT_ACME);
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista,
                                 status, canal, valor_total)
            VALUES (?, ?, ?, ?, now() + interval '2 days', now() + interval '2 days' + interval '2 hours',
                    'CONFIRMADA', 'BALCAO', 240.00)
            """, reservaId, TENANT_ACME, MODELO_ACME, clienteId);
        jdbc.update("""
            INSERT INTO reserva_lancamento (tenant_id, reserva_id, tipo, forma, valor, observacao)
            VALUES (?, ?, 'PAGAMENTO', 'PIX', 240.00, 'pagamento integral no balcão')
            """, TENANT_ACME, reservaId);
        jdbc.update("""
            INSERT INTO reserva_habilitacao (tenant_id, reserva_id, via, gru_numero, gru_pago,
                                             gru_gerada_em, resolvida)
            VALUES (?, ?, 'EMA', '608931002438599001', TRUE, now(), TRUE)
            """, TENANT_ACME, reservaId);
    }

    private RequestPostProcessor staff(String role) {
        return jwt().jwt(j -> j.subject(STAFF_USER.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String base() {
        return "/v1/tenants/" + TENANT_ACME + "/reservas/" + reservaId;
    }

    @Test
    @DisplayName("GET /ficha agrega cliente (CPF mascarado), extrato, habilitação e ciclo")
    void testFicha() throws Exception {
        mockMvc.perform(get(base() + "/ficha")
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff("GERENTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reserva.status").value("CONFIRMADA"))
            .andExpect(jsonPath("$.cliente.nome").value("Cliente Ficha"))
            .andExpect(jsonPath("$.cliente.documentoMascarado").value("***.***.444-55"))
            .andExpect(jsonPath("$.passeio.modeloNome").value("Ficha Modelo"))
            .andExpect(jsonPath("$.extrato.lancamentos", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.extrato.totalPagamentos").value(240.00))
            .andExpect(jsonPath("$.habilitacao.via").value("EMA"))
            .andExpect(jsonPath("$.ciclo.gruNumero").value("608931002438599001"))
            .andExpect(jsonPath("$.ciclo.marinhaEnviadaEm").isEmpty())
            .andExpect(jsonPath("$.aceite").isEmpty())
            .andExpect(jsonPath("$.documentos", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @DisplayName("Roles: FINANCEIRO 200; VENDEDOR 403 (ficha embute o extrato)")
    void testRoles() throws Exception {
        mockMvc.perform(get(base() + "/ficha")
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff("FINANCEIRO")))
            .andExpect(status().isOk());
        mockMvc.perform(get(base() + "/ficha")
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff("VENDEDOR")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Download-link gera PDF acessível sem auth (uso único)")
    void testDownloadLink() throws Exception {
        MvcResult res = mockMvc.perform(get(base() + "/ficha/download-link")
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff("GERENTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url", org.hamcrest.Matchers.containsString("/v1/pdf/")))
            .andReturn();

        String url = com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.url");
        String path = url.replaceFirst("^/api", "");

        byte[] pdf = mockMvc.perform(get(path))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        // uso único — segunda tentativa 404
        mockMvc.perform(get(path)).andExpect(status().isNotFound());
    }
}
