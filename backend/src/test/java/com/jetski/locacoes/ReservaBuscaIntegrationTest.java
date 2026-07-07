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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Módulo Reservas: busca com filtros server-side (status/canal/cliente/período)
 * e nomes resolvidos. Asserções autocontidas (endpoint é tenant-wide).
 */
@AutoConfigureMockMvc
@DisplayName("Reservas — Busca (módulo)")
class ReservaBuscaIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @MockBean OPAAuthorizationService opa;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID MODELO = UUID.fromString("77777777-7777-4777-8777-000000000081");
    private static final UUID STAFF_USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private UUID clienteId;
    private UUID confirmadaPortal;
    private UUID pendenteBalcao;

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());

        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'Busca Modelo', 'Yamaha', 110, 2, 100.00, 5, 50.00, 300.00, FALSE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, MODELO, TENANT_ACME);
        jdbc.update("DELETE FROM reserva WHERE cliente_id IN (SELECT id FROM cliente WHERE email = 'busca@test.com')");
        jdbc.update("DELETE FROM cliente WHERE email = 'busca@test.com'");

        clienteId = UUID.randomUUID();
        confirmadaPortal = UUID.randomUUID();
        pendenteBalcao = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO cliente (id, tenant_id, nome, email, origem, status_conta, ativo)
            VALUES (?, ?, 'Cliente Busca', 'busca@test.com', 'BALCAO', 'ATIVA', TRUE)
            """, clienteId, TENANT_ACME);
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista,
                                 status, canal, valor_total)
            VALUES (?, ?, ?, ?, now() + interval '5 days', now() + interval '5 days' + interval '2 hours',
                    'CONFIRMADA', 'PORTAL', 300.00)
            """, confirmadaPortal, TENANT_ACME, MODELO, clienteId);
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista,
                                 status, canal)
            VALUES (?, ?, ?, ?, now() + interval '40 days', now() + interval '40 days' + interval '1 hour',
                    'PENDENTE', 'BALCAO')
            """, pendenteBalcao, TENANT_ACME, MODELO, clienteId);
    }

    private RequestPostProcessor staff(String role) {
        return jwt().jwt(j -> j.subject(STAFF_USER.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private List<Map<String, Object>> buscar(String query, String role) throws Exception {
        String body = mockMvc.perform(get("/v1/tenants/" + TENANT_ACME + "/reservas/busca" + query)
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff(role)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$");
    }

    @Test
    @DisplayName("Filtros: status, canal, cliente e período — nomes resolvidos")
    void testFiltros() throws Exception {
        // por cliente: as duas reservas, com nomes resolvidos
        List<Map<String, Object>> porCliente = buscar("?clienteId=" + clienteId, "GERENTE");
        assertThat(porCliente).hasSize(2);
        assertThat(porCliente).allMatch(r -> "Cliente Busca".equals(r.get("clienteNome"))
            && "Busca Modelo".equals(r.get("modeloNome")));
        // ordenação desc: a de 40 dias vem primeiro
        assertThat(porCliente.get(0).get("id")).isEqualTo(pendenteBalcao.toString());

        // por status
        List<Map<String, Object>> confirmadas = buscar(
            "?clienteId=" + clienteId + "&status=CONFIRMADA", "GERENTE");
        assertThat(confirmadas).hasSize(1);
        assertThat(confirmadas.get(0).get("canal")).isEqualTo("PORTAL");
        assertThat(confirmadas.get(0).get("valorTotal")).isEqualTo(300.00);

        // por canal
        assertThat(buscar("?clienteId=" + clienteId + "&canal=BALCAO", "GERENTE")).hasSize(1);

        // por período: só a de daqui a 5 dias
        java.time.LocalDate hoje = java.time.LocalDate.now();
        List<Map<String, Object>> periodo = buscar(
            "?clienteId=" + clienteId + "&de=" + hoje + "T00:00:00&ate=" + hoje.plusDays(10) + "T23:59:59",
            "GERENTE");
        assertThat(periodo).hasSize(1);
        assertThat(periodo.get(0).get("id")).isEqualTo(confirmadaPortal.toString());
    }

    @Test
    @DisplayName("VENDEDOR também busca (mesmas roles do list)")
    void testVendedor() throws Exception {
        assertThat(buscar("?clienteId=" + clienteId, "VENDEDOR")).hasSize(2);
    }
}
