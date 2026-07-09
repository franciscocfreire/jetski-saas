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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CONTROLE DO DIA (prancheta digital do operador) + PRORROGAR.
 *
 * <p>Cenários: (a) EM_CURSO de ontem aparece na prancheta de hoje (vencida no
 * topo); (b) FINALIZADA do dia com dois pagamentos de formas diferentes no
 * folio → linha com as formas e totalPorForma (regime de caixa) refletindo;
 * (c) CANCELADA do dia aparece na lista mas fora das somas; (d) prorrogar:
 * EM_CURSO 200, FINALIZADA 400 (deny de negócio).
 *
 * <p>O DIA fica no passado (janela de caixa "quieta") para os agregados por
 * created_at não colidirem com fixtures de outras classes; a locação EM_CURSO
 * entra na prancheta independentemente da data (união com o dia).
 */
@AutoConfigureMockMvc
@DisplayName("Locações — Controle do Dia (prancheta) + prorrogar")
class ControleDoDiaIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @MockBean OPAAuthorizationService opa;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID STAFF_USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    // UUIDs de fixture EXCLUSIVOS desta classe (não reusar entre classes)
    private static final UUID MODELO = UUID.fromString("77777777-7777-4777-8777-0000000000a1");
    private static final UUID JET = UUID.fromString("77777777-7777-4777-8777-0000000000a2");
    private static final UUID VENDEDOR = UUID.fromString("77777777-7777-4777-8777-0000000000a3");
    /** Dia "quieto" no passado — janela de caixa sem interferência de outras classes. */
    private static final LocalDate DIA = LocalDate.now().minusDays(45);

    private UUID clienteId;

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());

        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'Controle Modelo', 'Yamaha', 110, 2, 100.00, 5, 50.00, 300.00, FALSE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, MODELO, TENANT_ACME);
        jdbc.update("""
            INSERT INTO jetski (id, tenant_id, modelo_id, serie, ano, horimetro_atual, status, ativo)
            VALUES (?, ?, ?, 'JET-CONTROLE-1', 2024, 1.0, 'DISPONIVEL', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, JET, TENANT_ACME, MODELO);
        jdbc.update("""
            INSERT INTO vendedor (id, tenant_id, nome, tipo, ativo)
            VALUES (?, ?, 'Vendedor Controle', 'INTERNO', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, VENDEDOR, TENANT_ACME);

        // limpeza idempotente das fixtures desta classe (locações do nosso jetski
        // + reservas do nosso cliente — FK impede apagar o cliente antes delas)
        jdbc.update("DELETE FROM reserva_lancamento WHERE locacao_id IN " +
            "(SELECT id FROM locacao WHERE jetski_id = ?)", JET);
        jdbc.update("DELETE FROM locacao WHERE jetski_id = ?", JET);
        jdbc.update("DELETE FROM reserva WHERE cliente_id IN " +
            "(SELECT id FROM cliente WHERE email = 'controle-do-dia@test.com')");
        jdbc.update("DELETE FROM cliente WHERE email = 'controle-do-dia@test.com'");

        clienteId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO cliente (id, tenant_id, nome, email, origem, status_conta, ativo)
            VALUES (?, ?, 'Cliente Controle', 'controle-do-dia@test.com', 'BALCAO', 'ATIVA', TRUE)
            """, clienteId, TENANT_ACME);
    }

    private UUID seedLocacao(LocalDateTime checkIn, LocalDateTime checkOut, int duracao,
                             String status, BigDecimal valorTotal, UUID vendedorId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO locacao (id, tenant_id, jetski_id, cliente_id, vendedor_id,
                                 data_check_in, data_check_out, horimetro_inicio,
                                 duracao_prevista, valor_total, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, 10.00, ?, ?, ?)
            """, id, TENANT_ACME, JET, clienteId, vendedorId,
            Timestamp.valueOf(checkIn), checkOut != null ? Timestamp.valueOf(checkOut) : null,
            duracao, valorTotal, status);
        return id;
    }

    /** Reserva com pagamento CONFIRMADO (prontidão: só o pagamento OK — sem habilitação/termo). */
    private UUID seedReserva(LocalDate dia, String hora, String status, UUID vendedorId,
                             BigDecimal valorTotal) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, vendedor_id,
                                 data_inicio, data_fim_prevista, status, canal,
                                 pagamento_status, valor_total)
            VALUES (?, ?, ?, ?, ?, ?::timestamp, ?::timestamp + interval '90 minutes',
                    ?, 'BALCAO', 'CONFIRMADO', ?)
            """, id, TENANT_ACME, MODELO, clienteId, vendedorId,
            dia + "T" + hora, dia + "T" + hora, status, valorTotal);
        return id;
    }

    private void seedPagamento(UUID locacaoId, String forma, String valor, LocalDateTime criadoEm) {
        jdbc.update("""
            INSERT INTO reserva_lancamento (tenant_id, locacao_id, tipo, forma, valor, created_at)
            VALUES (?, ?, 'PAGAMENTO', ?, ?::numeric, ?)
            """, TENANT_ACME, locacaoId, forma, valor, Timestamp.valueOf(criadoEm));
    }

    private RequestPostProcessor staff() {
        return jwt().jwt(j -> j.subject(STAFF_USER.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> controleDoDia() throws Exception {
        String body = mockMvc.perform(get("/v1/tenants/{t}/locacoes/controle-do-dia", TENANT_ACME)
                .param("data", DIA.toString())
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$");
    }

    private static int indexOf(List<Map<String, Object>> linhas, UUID locacaoId) {
        for (int i = 0; i < linhas.size(); i++) {
            if (locacaoId.toString().equals(linhas.get(i).get("locacaoId"))) {
                return i;
            }
        }
        return -1;
    }

    /** Índice da linha RESERVA pelo reservaId (-1 se ausente). */
    private static int indexOfReserva(List<Map<String, Object>> linhas, UUID reservaId) {
        for (int i = 0; i < linhas.size(); i++) {
            if (reservaId.toString().equals(linhas.get(i).get("reservaId"))) {
                return i;
            }
        }
        return -1;
    }

    private static void assertValor(Object atual, String esperado) {
        assertThat(new BigDecimal(String.valueOf(atual)))
            .isEqualByComparingTo(new BigDecimal(esperado));
    }

    @Test
    @DisplayName("Prancheta: EM_CURSO de ontem no topo; FINALIZADA com formas do folio; CANCELADA fora das somas")
    @SuppressWarnings("unchecked")
    void testControleDoDia() throws Exception {
        // (a) EM_CURSO de ONTEM — sem check-out, sem valor apurado ainda
        UUID emCursoOntem = seedLocacao(DIA.minusDays(1).atTime(9, 0), null, 60,
            "EM_CURSO", null, null);

        // (b) FINALIZADA do dia com 2 pagamentos de formas diferentes no folio
        UUID finalizada = seedLocacao(DIA.atTime(10, 0), DIA.atTime(11, 0), 60,
            "FINALIZADA", new BigDecimal("300.00"), VENDEDOR);
        seedPagamento(finalizada, "PIX", "200.00", DIA.atTime(12, 0));
        seedPagamento(finalizada, "DINHEIRO", "100.00", DIA.atTime(12, 30));

        // (c) CANCELADA do dia — aparece na linha, mas fora das somas
        UUID cancelada = seedLocacao(DIA.atTime(12, 0), null, 30,
            "CANCELADA", new BigDecimal("100.00"), null);

        Map<String, Object> resp = controleDoDia();
        List<Map<String, Object>> linhas = (List<Map<String, Object>>) resp.get("linhas");

        // as três locações aparecem; EM_CURSO (mesmo sendo de ontem) vem antes das demais
        int idxEmCurso = indexOf(linhas, emCursoOntem);
        int idxFinalizada = indexOf(linhas, finalizada);
        int idxCancelada = indexOf(linhas, cancelada);
        assertThat(idxEmCurso).isNotNegative();
        assertThat(idxFinalizada).isNotNegative();
        assertThat(idxCancelada).isNotNegative();
        assertThat(idxEmCurso).isLessThan(idxFinalizada);
        assertThat(idxEmCurso).isLessThan(idxCancelada);

        Map<String, Object> linhaEmCurso = linhas.get(idxEmCurso);
        assertThat(linhaEmCurso.get("status")).isEqualTo("EM_CURSO");
        assertThat(linhaEmCurso.get("dataCheckOut")).isNull();
        assertThat(linhaEmCurso.get("jetskiSerie")).isEqualTo("JET-CONTROLE-1");
        assertThat(linhaEmCurso.get("clienteNome")).isEqualTo("Cliente Controle");

        // linha finalizada: formas distintas em ordem cronológica + nomes resolvidos
        Map<String, Object> linhaFinalizada = linhas.get(idxFinalizada);
        assertThat((List<String>) linhaFinalizada.get("formas"))
            .containsExactly("PIX", "DINHEIRO");
        assertThat(linhaFinalizada.get("vendedorNome")).isEqualTo("Vendedor Controle");
        assertValor(linhaFinalizada.get("valorTotal"), "300.00");

        // totalPorForma = REGIME DE CAIXA (data do lançamento no folio)
        Map<String, Object> totalPorForma = (Map<String, Object>) resp.get("totalPorForma");
        assertValor(totalPorForma.get("PIX"), "200.00");
        assertValor(totalPorForma.get("DINHEIRO"), "100.00");

        // totalDia/totalPorVendedor = COMPETÊNCIA das linhas NÃO CANCELADAS do
        // dia: só a finalizada (300) — cancelada (100) fora, em curso sem valor
        assertValor(resp.get("totalDia"), "300.00");
        List<Map<String, Object>> porVendedor = (List<Map<String, Object>>) resp.get("totalPorVendedor");
        assertThat(porVendedor).hasSize(1);
        assertThat(porVendedor.get(0).get("vendedorId")).isEqualTo(VENDEDOR.toString());
        assertThat(porVendedor.get(0).get("vendedorNome")).isEqualTo("Vendedor Controle");
        assertValor(porVendedor.get(0).get("total"), "300.00");
    }

    @Test
    @DisplayName("Prorrogar: EM_CURSO 200 com nova duração; FINALIZADA e mínimo <5min dão 400")
    void testProrrogar() throws Exception {
        UUID emCurso = seedLocacao(DIA.atTime(9, 0), null, 60, "EM_CURSO", null, null);
        UUID finalizada = seedLocacao(DIA.atTime(10, 0), DIA.atTime(11, 0), 60,
            "FINALIZADA", new BigDecimal("300.00"), null);

        // (d1) EM_CURSO: prorroga de 60 para 240 minutos
        mockMvc.perform(patch("/v1/tenants/{t}/locacoes/{id}/duracao", TENANT_ACME, emCurso)
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"duracaoPrevista\": 240}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.duracaoPrevista").value(240))
            .andExpect(jsonPath("$.status").value("EM_CURSO"));

        Integer persistida = jdbc.queryForObject(
            "SELECT duracao_prevista FROM locacao WHERE id = ?", Integer.class, emCurso);
        assertThat(persistida).isEqualTo(240);

        // (d2) FINALIZADA: deny de NEGÓCIO (400), não de autorização (403)
        mockMvc.perform(patch("/v1/tenants/{t}/locacoes/{id}/duracao", TENANT_ACME, finalizada)
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"duracaoPrevista\": 240}"))
            .andExpect(status().isBadRequest());

        // (d3) mínimo de 5 minutos
        mockMvc.perform(patch("/v1/tenants/{t}/locacoes/{id}/duracao", TENANT_ACME, emCurso)
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"duracaoPrevista\": 3}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Prancheta v2: reserva CONFIRMADA do dia vira linha RESERVA (prontidão presente, " +
                 "fora das somas); EXPIRADA e outro dia ficam fora")
    @SuppressWarnings("unchecked")
    void testReservasNaPrancheta() throws Exception {
        // Locação FINALIZADA do dia — âncora das somas (competência)
        UUID finalizada = seedLocacao(DIA.atTime(10, 0), DIA.atTime(11, 0), 60,
            "FINALIZADA", new BigDecimal("300.00"), VENDEDOR);

        // (a) reserva CONFIRMADA do dia com vendedor e pagamento CONFIRMADO
        UUID confirmada = seedReserva(DIA, "14:00:00", "CONFIRMADA", VENDEDOR,
            new BigDecimal("250.00"));
        // (b) fora da prancheta: EXPIRADA do dia e CONFIRMADA de outro dia
        UUID expirada = seedReserva(DIA, "15:00:00", "EXPIRADA", null, null);
        UUID outroDia = seedReserva(DIA.plusDays(1), "10:00:00", "CONFIRMADA", null, null);

        Map<String, Object> resp = controleDoDia();
        List<Map<String, Object>> linhas = (List<Map<String, Object>>) resp.get("linhas");

        // (a) linha RESERVA presente, ANTES das finalizadas (grupo: em curso →
        //     reservas → demais), com os campos mapeados e o trio de prontidão
        int idxReserva = indexOfReserva(linhas, confirmada);
        int idxFinalizada = indexOf(linhas, finalizada);
        assertThat(idxReserva).isNotNegative();
        assertThat(idxFinalizada).isNotNegative();
        assertThat(idxReserva).isLessThan(idxFinalizada);

        Map<String, Object> linhaReserva = linhas.get(idxReserva);
        assertThat(linhaReserva.get("tipo")).isEqualTo("RESERVA");
        assertThat(linhaReserva.get("locacaoId")).isNull();
        assertThat(linhaReserva.get("status")).isEqualTo("CONFIRMADA");
        assertThat(linhaReserva.get("jetskiSerie")).isNull(); // portal-style: sem alocação
        assertThat(linhaReserva.get("modeloNome")).isEqualTo("Controle Modelo");
        assertThat(linhaReserva.get("clienteNome")).isEqualTo("Cliente Controle");
        assertThat(linhaReserva.get("vendedorNome")).isEqualTo("Vendedor Controle");
        assertThat(linhaReserva.get("dataCheckOut")).isNull();
        assertThat(linhaReserva.get("duracaoPrevista")).isEqualTo(90);
        assertValor(linhaReserva.get("valorTotal"), "250.00");
        assertThat((List<String>) linhaReserva.get("formas")).isEmpty();
        // prontidão: pagamento OK; sem habilitação/termo → não pronta
        assertThat(linhaReserva.get("pagamentoOk")).isEqualTo(Boolean.TRUE);
        assertThat(linhaReserva.get("habilitacaoOk")).isEqualTo(Boolean.FALSE);
        assertThat(linhaReserva.get("termoOk")).isEqualTo(Boolean.FALSE);
        assertThat(linhaReserva.get("prontaParaCheckin")).isEqualTo(Boolean.FALSE);

        // linha LOCACAO: tipo preenchido, modelo resolvido via jetski,
        // trio de prontidão null (conceito de reserva)
        Map<String, Object> linhaLocacao = linhas.get(idxFinalizada);
        assertThat(linhaLocacao.get("tipo")).isEqualTo("LOCACAO");
        assertThat(linhaLocacao.get("modeloNome")).isEqualTo("Controle Modelo");
        assertThat(linhaLocacao.get("pagamentoOk")).isNull();
        assertThat(linhaLocacao.get("prontaParaCheckin")).isNull();

        // (b) EXPIRADA do dia e CONFIRMADA de outro dia NÃO aparecem
        assertThat(indexOfReserva(linhas, expirada)).isEqualTo(-1);
        assertThat(indexOfReserva(linhas, outroDia)).isEqualTo(-1);

        // linhas RESERVA ficam FORA de totalDia/totalPorVendedor (só locações)
        assertValor(resp.get("totalDia"), "300.00");
        List<Map<String, Object>> porVendedor = (List<Map<String, Object>>) resp.get("totalPorVendedor");
        assertThat(porVendedor).hasSize(1);
        assertValor(porVendedor.get(0).get("total"), "300.00");
    }

    @Test
    @DisplayName("PATCH vendedor: EM_CURSO 200 aplica (e null desassocia); FINALIZADA 400 (negócio)")
    void testAlterarVendedorLocacao() throws Exception {
        UUID emCurso = seedLocacao(DIA.atTime(9, 0), null, 60, "EM_CURSO", null, null);
        UUID finalizada = seedLocacao(DIA.atTime(10, 0), DIA.atTime(11, 0), 60,
            "FINALIZADA", new BigDecimal("300.00"), null);

        // (c1) EM_CURSO: associa o vendedor
        mockMvc.perform(patch("/v1/tenants/{t}/locacoes/{id}/vendedor", TENANT_ACME, emCurso)
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vendedorId\": \"" + VENDEDOR + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("EM_CURSO"));

        UUID persistido = jdbc.queryForObject(
            "SELECT vendedor_id FROM locacao WHERE id = ?", UUID.class, emCurso);
        assertThat(persistido).isEqualTo(VENDEDOR);

        // (c2) null desassocia
        mockMvc.perform(patch("/v1/tenants/{t}/locacoes/{id}/vendedor", TENANT_ACME, emCurso)
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vendedorId\": null}"))
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
            "SELECT vendedor_id FROM locacao WHERE id = ?", UUID.class, emCurso)).isNull();

        // (c3) FINALIZADA: deny de NEGÓCIO (400) — o caminho certo é editar-finalizada
        mockMvc.perform(patch("/v1/tenants/{t}/locacoes/{id}/vendedor", TENANT_ACME, finalizada)
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vendedorId\": \"" + VENDEDOR + "\"}"))
            .andExpect(status().isBadRequest());

        // (c4) vendedor inexistente: 400 (validação de negócio)
        mockMvc.perform(patch("/v1/tenants/{t}/locacoes/{id}/vendedor", TENANT_ACME, emCurso)
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vendedorId\": \"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT reserva com vendedorId aplica; omitir o campo NÃO remove o vendedor")
    void testUpdateReservaVendedor() throws Exception {
        UUID reserva = seedReserva(DIA, "14:00:00", "CONFIRMADA", null, new BigDecimal("250.00"));

        // (d1) aplica o vendedor
        mockMvc.perform(put("/v1/tenants/{t}/reservas/{id}", TENANT_ACME, reserva)
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vendedorId\": \"" + VENDEDOR + "\"}"))
            .andExpect(status().isOk());

        UUID persistido = jdbc.queryForObject(
            "SELECT vendedor_id FROM reserva WHERE id = ?", UUID.class, reserva);
        assertThat(persistido).isEqualTo(VENDEDOR);

        // (d2) update SEM o campo não mexe no vendedor (semântica só-aplica-se-presente)
        mockMvc.perform(put("/v1/tenants/{t}/reservas/{id}", TENANT_ACME, reserva)
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"observacoes\": \"sem vendedor no body\"}"))
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
            "SELECT vendedor_id FROM reserva WHERE id = ?", UUID.class, reserva)).isEqualTo(VENDEDOR);

        // (d3) vendedor inexistente: 400 (validação de negócio)
        mockMvc.perform(put("/v1/tenants/{t}/reservas/{id}", TENANT_ACME, reserva)
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vendedorId\": \"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
    }
}
