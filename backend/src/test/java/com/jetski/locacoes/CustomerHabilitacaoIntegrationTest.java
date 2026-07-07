package com.jetski.locacoes;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.security.UserProvisioningService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CHA-MTA-E temporária (30 dias): listagem cross-loja das emissões do cliente
 * e REUSO automático na triagem de nova reserva (via CHA derivada — sem nova
 * GRU, sem envio à Marinha).
 */
@AutoConfigureMockMvc
@DisplayName("Portal do Cliente — Habilitações temporárias (30 dias)")
class CustomerHabilitacaoIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @MockBean OPAAuthorizationService opa;
    @MockBean UserProvisioningService provisioning;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID TENANT_MARINA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID MODELO_ACME = UUID.fromString("77777777-7777-4777-8777-000000000061");
    private static final UUID MODELO_MARINA = UUID.fromString("77777777-7777-4777-8777-000000000062");
    private static final String SUB = "abababab-0000-0000-0000-000000000001";
    private static final String GRU = "60893100243859999";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private UUID clienteAcme;

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());
        when(provisioning.definirCpf(anyString(), anyString())).thenReturn(true);
        when(provisioning.updateUserName(anyString(), anyString())).thenReturn(true);

        // lojas no marketplace com modelos
        jdbc.update("UPDATE tenant SET exibir_no_marketplace = true, status = 'ATIVO' WHERE id IN (?, ?)",
            TENANT_ACME, TENANT_MARINA);
        for (Object[] m : new Object[][]{
                {MODELO_ACME, TENANT_ACME, "Hab Modelo Acme"},
                {MODELO_MARINA, TENANT_MARINA, "Hab Modelo Marina"}}) {
            jdbc.update("""
                INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                    preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                    inclui_combustivel, ativo, exibir_no_marketplace)
                VALUES (?, ?, ?, 'Sea-Doo', 130, 2, 150.00, 5, 50.00, 300.00, FALSE, TRUE, TRUE)
                ON CONFLICT (id) DO NOTHING
                """, m[0], m[1], m[2]);
        }

        // limpeza (FKs primeiro)
        jdbc.update("DELETE FROM reserva_habilitacao WHERE reserva_id IN " +
            "(SELECT id FROM reserva WHERE cliente_id IN (SELECT id FROM cliente WHERE email = 'chatemp@test.com'))");
        jdbc.update("DELETE FROM reserva WHERE cliente_id IN (SELECT id FROM cliente WHERE email = 'chatemp@test.com')");
        jdbc.update("DELETE FROM cliente_identity_provider WHERE provider_user_id = ?", SUB);
        jdbc.update("DELETE FROM cliente WHERE email = 'chatemp@test.com'");
        jdbc.update("DELETE FROM customer_profile WHERE provider_user_id = ?", SUB);

        // cliente vinculado na ACME
        clienteAcme = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO cliente (id, tenant_id, nome, email, documento, origem, status_conta, ativo)
            VALUES (?, ?, 'Cliente ChaTemp', 'chatemp@test.com', '741.852.963-00', 'PORTAL', 'ATIVA', TRUE)
            """, clienteAcme, TENANT_ACME);
        jdbc.update("""
            INSERT INTO cliente_identity_provider (tenant_id, cliente_id, provider, provider_user_id)
            VALUES (?, ?, 'keycloak', ?)
            """, TENANT_ACME, clienteAcme, SUB);
    }

    private RequestPostProcessor cliente() {
        return jwt().jwt(j -> j.subject(SUB)
                .claim("name", "Cliente ChaTemp")
                .claim("email", "chatemp@test.com")
                .claim("email_verified", true))
            .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    /**
     * Emissão EMA na ACME: reserva emitida + habilitação EMA com GRU paga.
     * {@code confirmada} = devolutiva da Marinha anexada (pré-requisito do reuso).
     */
    private UUID seedEmissaoAcme(String intervaloEmissao, boolean confirmada) {
        UUID reservaId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista,
                                 status, canal, documento_emitido_em)
            VALUES (?, ?, ?, ?, now() - interval '2 days', now() - interval '2 days' + interval '2 hours',
                    'CONFIRMADA', 'PORTAL', now() - interval '%s')
            """.formatted(intervaloEmissao), reservaId, TENANT_ACME, MODELO_ACME, clienteAcme);
        if (confirmada) {
            jdbc.update("""
                INSERT INTO reserva_habilitacao (tenant_id, reserva_id, via, gru_numero, gru_pago,
                                                 resolvida, marinha_confirmada_em, cha_mtae_s3_key)
                VALUES (?, ?, 'EMA', ?, TRUE, TRUE, now(), ?)
                """, TENANT_ACME, reservaId, GRU,
                TENANT_ACME + "/reserva/" + reservaId + "/cha-mtae-confirmada.pdf");
        } else {
            jdbc.update("""
                INSERT INTO reserva_habilitacao (tenant_id, reserva_id, via, gru_numero, gru_pago, resolvida)
                VALUES (?, ?, 'EMA', ?, TRUE, TRUE)
                """, TENANT_ACME, reservaId, GRU);
        }
        return reservaId;
    }

    private String criarReserva(String lojaSlug, UUID modeloId) throws Exception {
        return criarReserva(lojaSlug, modeloId, null);
    }

    private String criarReserva(String lojaSlug, UUID modeloId, Boolean usarTemporaria) throws Exception {
        LocalDateTime inicio = LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);
        String extra = usarTemporaria == null ? "" : ",\"usarTemporaria\":" + usarTemporaria;
        MvcResult res = mockMvc.perform(post("/v1/customers/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"lojaSlug":"%s","modeloId":"%s","dataInicio":"%s","dataFimPrevista":"%s",
                     "pagamentoTipo":"SINAL","possuiCha":false%s}
                    """.formatted(lojaSlug, modeloId, ISO.format(inicio), ISO.format(inicio.plusHours(2)), extra))
                .with(cliente()))
            .andExpect(status().isCreated())
            .andReturn();
        return com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    @Test
    @DisplayName("GET /habilitacoes lista a temporária vigente com GRU, validade e confirmação")
    void testListagem() throws Exception {
        seedEmissaoAcme("1 day", true);

        mockMvc.perform(get("/v1/customers/habilitacoes").with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$[0].gruNumero").value(GRU))
            .andExpect(jsonPath("$[0].vigente").value(true))
            .andExpect(jsonPath("$[0].confirmada").value(true))
            .andExpect(jsonPath("$[0].lojaSlug").value("acme"))
            .andExpect(jsonPath("$[0].validaAte").isNotEmpty());
    }

    @Test
    @DisplayName("Sem devolutiva da Marinha NÃO reusa — 'aguardando confirmação', nova reserva cai em EMA")
    void testNaoConfirmadaNaoReusa() throws Exception {
        seedEmissaoAcme("1 day", false);

        mockMvc.perform(get("/v1/customers/habilitacoes").with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].vigente").value(true))
            .andExpect(jsonPath("$[0].confirmada").value(false));

        String novaId = criarReserva("acme", MODELO_ACME);
        String via = jdbc.queryForObject(
            "SELECT via FROM reserva_habilitacao WHERE reserva_id = ?::uuid", String.class, novaId);
        assertThat(via).isEqualTo("EMA");
    }

    @Test
    @DisplayName("usarTemporaria=false: cliente opta por emitir nova mesmo com confirmada vigente")
    void testUsarTemporariaFalse() throws Exception {
        seedEmissaoAcme("1 day", true);

        String novaId = criarReserva("acme", MODELO_ACME, false);
        String via = jdbc.queryForObject(
            "SELECT via FROM reserva_habilitacao WHERE reserva_id = ?::uuid", String.class, novaId);
        assertThat(via).isEqualTo("EMA");
    }

    @Test
    @DisplayName("Troca pós-criação: usar-temporaria aplica a CHA derivada; emitir-nova desfaz")
    void testTrocaPosCriacao() throws Exception {
        seedEmissaoAcme("1 day", true);

        // nasce EMA por opção do cliente
        String novaId = criarReserva("acme", MODELO_ACME, false);

        // aplica a temporária depois
        mockMvc.perform(post("/v1/customers/reservas/{id}/habilitacao/usar-temporaria", novaId)
                .with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.via").value("CHA"))
            .andExpect(jsonPath("$.chaCategoria").value("MTA-E TEMPORÁRIA"))
            .andExpect(jsonPath("$.chaNumero").value(GRU))
            .andExpect(jsonPath("$.resolvida").value(true));

        // e desfaz — volta ao fluxo EMA
        mockMvc.perform(post("/v1/customers/reservas/{id}/habilitacao/emitir-nova", novaId)
                .with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.via").value("EMA"))
            .andExpect(jsonPath("$.resolvida").value(false));

        // emitir-nova sobre habilitação que NÃO é reuso → 400
        mockMvc.perform(post("/v1/customers/reservas/{id}/habilitacao/emitir-nova", novaId)
                .with(cliente()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Nova reserva com temporária vigente REUSA (via CHA derivada, sem GRU nova) + resposta e auditoria")
    void testReusoAutomatico() throws Exception {
        seedEmissaoAcme("1 day", true);

        String novaId = criarReserva("acme", MODELO_ACME);

        // resposta da criação anuncia o reuso
        mockMvc.perform(get("/v1/customers/reservas/{id}/checklist", novaId).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.habilitacaoOk").value(true))
            .andExpect(jsonPath("$.habilitacaoVia").value("CHA"))
            .andExpect(jsonPath("$.habilitacaoTemporaria.gruNumero").value(GRU));

        // habilitação derivada: via CHA marcada, sem GRU própria
        var row = jdbc.queryForMap(
            "SELECT via, cha_categoria, cha_numero, gru_numero, resolvida " +
            "FROM reserva_habilitacao WHERE reserva_id = ?::uuid", novaId);
        assertThat(row.get("via")).isEqualTo("CHA");
        assertThat(row.get("cha_categoria")).isEqualTo("MTA-E TEMPORÁRIA");
        assertThat(row.get("cha_numero")).isEqualTo(GRU);
        assertThat(row.get("gru_numero")).isNull();
        assertThat(row.get("resolvida")).isEqualTo(true);

        // auditoria assíncrona do reuso (poll limitado)
        Integer count = 0;
        for (int i = 0; i < 50 && count == 0; i++) {
            count = jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE acao = 'HABILITACAO_TEMPORARIA_REUSADA' " +
                "AND entidade_id = ?::uuid", Integer.class, novaId);
            Thread.sleep(100);
        }
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Temporária EXPIRADA (31 dias) não reusa — nova reserva cai em via EMA")
    void testExpiradaNaoReusa() throws Exception {
        seedEmissaoAcme("31 days", true);

        mockMvc.perform(get("/v1/customers/habilitacoes").with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].vigente").value(false));

        String novaId = criarReserva("acme", MODELO_ACME);
        String via = jdbc.queryForObject(
            "SELECT via FROM reserva_habilitacao WHERE reserva_id = ?::uuid", String.class, novaId);
        assertThat(via).isEqualTo("EMA");
    }

    @Test
    @DisplayName("Cross-loja: emissão na ACME reusada em reserva na MARINA (write no tenant certo)")
    void testReusoCrossLoja() throws Exception {
        seedEmissaoAcme("1 day", true);

        String novaId = criarReserva("marina-bay", MODELO_MARINA);

        var row = jdbc.queryForMap(
            "SELECT tenant_id, via, cha_categoria, cha_numero FROM reserva_habilitacao " +
            "WHERE reserva_id = ?::uuid", novaId);
        assertThat(row.get("tenant_id")).isEqualTo(TENANT_MARINA);
        assertThat(row.get("via")).isEqualTo("CHA");
        assertThat(row.get("cha_categoria")).isEqualTo("MTA-E TEMPORÁRIA");
        assertThat(row.get("cha_numero")).isEqualTo(GRU);
    }
}
