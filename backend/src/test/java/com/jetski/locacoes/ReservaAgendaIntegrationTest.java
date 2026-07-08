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

import java.time.LocalDate;
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
 * Visão AGENDA (grade por jetski): reservas do dia com prontidão em lote —
 * pagamento/habilitação/termo — sem N+1; RASCUNHO e outros dias ficam fora.
 */
@AutoConfigureMockMvc
@DisplayName("Reservas — Agenda do dia (grade + prontidão)")
class ReservaAgendaIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @MockBean OPAAuthorizationService opa;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID MODELO = UUID.fromString("77777777-7777-4777-8777-000000000091");
    private static final UUID JET = UUID.fromString("77777777-7777-4777-8777-000000000092");
    private static final UUID STAFF_USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate DIA = LocalDate.now().plusDays(10);

    private UUID clienteId;

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());

        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'Agenda Modelo', 'Yamaha', 110, 2, 100.00, 5, 50.00, 300.00, FALSE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, MODELO, TENANT_ACME);
        jdbc.update("""
            INSERT INTO jetski (id, tenant_id, modelo_id, serie, ano, horimetro_atual, status, ativo)
            VALUES (?, ?, ?, 'JET-AGENDA-1', 2024, 1.0, 'DISPONIVEL', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, JET, TENANT_ACME, MODELO);

        jdbc.update("DELETE FROM reserva_aceite WHERE reserva_id IN " +
            "(SELECT id FROM reserva WHERE cliente_id IN (SELECT id FROM cliente WHERE email = 'agenda@test.com'))");
        jdbc.update("DELETE FROM reserva_habilitacao WHERE reserva_id IN " +
            "(SELECT id FROM reserva WHERE cliente_id IN (SELECT id FROM cliente WHERE email = 'agenda@test.com'))");
        jdbc.update("DELETE FROM reserva WHERE cliente_id IN (SELECT id FROM cliente WHERE email = 'agenda@test.com')");
        jdbc.update("DELETE FROM cliente WHERE email = 'agenda@test.com'");

        clienteId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO cliente (id, tenant_id, nome, email, origem, status_conta, ativo)
            VALUES (?, ?, 'Cliente Agenda', 'agenda@test.com', 'BALCAO', 'ATIVA', TRUE)
            """, clienteId, TENANT_ACME);
    }

    private UUID seedReserva(String hora, String status, boolean comJet, String pagamentoStatus) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, jetski_id, cliente_id,
                                 data_inicio, data_fim_prevista, status, canal, pagamento_status)
            VALUES (?, ?, ?, ?, ?, ?::timestamp, ?::timestamp + interval '2 hours', ?, 'BALCAO', ?)
            """, id, TENANT_ACME, MODELO, comJet ? JET : null, clienteId,
            DIA + "T" + hora, DIA + "T" + hora, status, pagamentoStatus);
        return id;
    }

    private RequestPostProcessor staff() {
        return jwt().jwt(j -> j.subject(STAFF_USER.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"));
    }

    private List<Map<String, Object>> agenda() throws Exception {
        String body = mockMvc.perform(get("/v1/tenants/{t}/reservas/agenda", TENANT_ACME)
                .param("data", DIA.toString())
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$");
    }

    @Test
    @DisplayName("Prontidão em lote: pronta / habilitação pendente / sem jet (A alocar); RASCUNHO e outro dia fora")
    void testAgendaDoDia() throws Exception {
        // (a) pronta: jet + pagamento CONFIRMADO + habilitação resolvida + aceite
        UUID pronta = seedReserva("10:00:00", "CONFIRMADA", true, "CONFIRMADO");
        jdbc.update("""
            INSERT INTO reserva_habilitacao (tenant_id, reserva_id, via, cha_numero, resolvida)
            VALUES (?, ?, 'CHA', 'CHA-99', TRUE)
            """, TENANT_ACME, pronta);
        jdbc.update("""
            INSERT INTO reserva_aceite (tenant_id, reserva_id, metodo, assinatura_s3_key,
                                        hash_sha256, origem, aceito_em)
            VALUES (?, ?, 'SIGNATURE_PAD', 't/r/a.png', 'h', 'BALCAO', now())
            """, TENANT_ACME, pronta);

        // (b) habilitação EMA não resolvida, pagamento pendente
        UUID pendente = seedReserva("13:00:00", "PENDENTE", true, "AGUARDANDO");
        jdbc.update("""
            INSERT INTO reserva_habilitacao (tenant_id, reserva_id, via, resolvida)
            VALUES (?, ?, 'EMA', FALSE)
            """, TENANT_ACME, pendente);

        // (c) portal sem jet — faixa "A alocar"
        UUID semJet = seedReserva("15:00:00", "CONFIRMADA", false, "CONFIRMADO");

        // (d) RASCUNHO fora; (e) outro dia fora
        seedReserva("16:00:00", "RASCUNHO", true, "AGUARDANDO");
        UUID outroDia = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista,
                                 status, canal)
            VALUES (?, ?, ?, ?, ?::timestamp, ?::timestamp + interval '1 hour', 'CONFIRMADA', 'BALCAO')
            """, outroDia, TENANT_ACME, MODELO, clienteId,
            DIA.plusDays(1) + "T10:00:00", DIA.plusDays(1) + "T10:00:00");

        List<Map<String, Object>> itens = agenda();
        assertThat(itens).hasSize(3);
        // ordenado por horário
        assertThat(itens.get(0).get("id")).isEqualTo(pronta.toString());

        Map<String, Object> a = itens.get(0);
        assertThat(a.get("clienteNome")).isEqualTo("Cliente Agenda");
        assertThat(a.get("jetskiSerie")).isEqualTo("JET-AGENDA-1");
        assertThat(a.get("pagamentoOk")).isEqualTo(true);
        assertThat(a.get("habilitacaoOk")).isEqualTo(true);
        assertThat(a.get("habilitacaoVia")).isEqualTo("CHA");
        assertThat(a.get("termoOk")).isEqualTo(true);
        assertThat(a.get("prontaParaCheckin")).isEqualTo(true);

        Map<String, Object> b = itens.get(1);
        assertThat(b.get("id")).isEqualTo(pendente.toString());
        assertThat(b.get("pagamentoOk")).isEqualTo(false);
        assertThat(b.get("habilitacaoOk")).isEqualTo(false);
        assertThat(b.get("prontaParaCheckin")).isEqualTo(false);

        Map<String, Object> c = itens.get(2);
        assertThat(c.get("id")).isEqualTo(semJet.toString());
        assertThat(c.get("jetskiId")).isNull();
        assertThat(c.get("modeloNome")).isEqualTo("Agenda Modelo");
    }

    @Test
    @DisplayName("Período (?ate=): visão semana devolve dias distintos; sem ate continua um dia")
    void testPeriodo() throws Exception {
        seedReserva("10:00:00", "CONFIRMADA", true, "CONFIRMADO");
        UUID depois = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista,
                                 status, canal)
            VALUES (?, ?, ?, ?, ?::timestamp, ?::timestamp + interval '1 hour', 'CONFIRMADA', 'BALCAO')
            """, depois, TENANT_ACME, MODELO, clienteId,
            DIA.plusDays(3) + "T09:00:00", DIA.plusDays(3) + "T09:00:00");

        // só o dia: 1 reserva
        assertThat(agenda()).hasSize(1);

        // semana: as duas
        String body = mockMvc.perform(get("/v1/tenants/{t}/reservas/agenda", TENANT_ACME)
                .param("data", DIA.toString())
                .param("ate", DIA.plusDays(6).toString())
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> semana = com.jayway.jsonpath.JsonPath.read(body, "$");
        assertThat(semana).hasSize(2);
        assertThat(semana.get(1).get("id")).isEqualTo(depois.toString());
    }
}
