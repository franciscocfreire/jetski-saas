package com.jetski.locacoes;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.locacoes.internal.CustomerReservaService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
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
 * P1 do Portal do Cliente: reserva online (cria/vincula Cliente no 1º contato),
 * PIX com valor exato, comprovante → EM_ANALISE → fila staff, checklist e
 * expiração de pré-reserva; vitrine e disponibilidade públicas por loja.
 */
@AutoConfigureMockMvc
@DisplayName("Portal do Cliente — P1 (reservas)")
class CustomerReservaIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerReservaService customerReservaService;

    @MockBean OPAAuthorizationService opa;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MODELO_ID = UUID.fromString("77777777-7777-4777-8777-000000000001");
    private static final UUID JETSKI_ID = UUID.fromString("77777777-7777-4777-8777-000000000002");
    private static final String SUB = "dddddddd-0000-0000-0000-000000000001";

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());

        // Loja no marketplace + PIX configurado
        jdbc.update("UPDATE tenant SET exibir_no_marketplace = true, pix_chave = 'pix@acme.com.br', " +
                    "cidade = 'Florianopolis' WHERE id = ?", TENANT_ACME);

        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo, exibir_no_marketplace)
            VALUES (?, ?, 'GTX Portal 170', 'Sea-Doo', 170, 2, 200.00, 5, 50.00, 300.00, FALSE, TRUE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, MODELO_ID, TENANT_ACME);
        jdbc.update("UPDATE modelo SET exibir_no_marketplace = true, ativo = true WHERE id = ?", MODELO_ID);
        jdbc.update("""
            INSERT INTO jetski (id, tenant_id, modelo_id, serie, ano, horimetro_atual, status, ativo)
            VALUES (?, ?, ?, 'JET-PORTAL-1', 2024, 10.0, 'DISPONIVEL', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, JETSKI_ID, TENANT_ACME, MODELO_ID);
        jdbc.update("UPDATE jetski SET status = 'DISPONIVEL', ativo = true WHERE id = ?", JETSKI_ID);

        // Limpa artefatos deste teste (sub/e-mail fixos)
        jdbc.update("DELETE FROM reserva_comprovante WHERE tenant_id = ?", TENANT_ACME);
        jdbc.update("DELETE FROM reserva WHERE tenant_id = ? AND canal = 'PORTAL'", TENANT_ACME);
        // Reservas de BALCÃO criadas pelo teste de pagamento presencial (FK → cliente)
        jdbc.update("DELETE FROM reserva WHERE tenant_id = ? AND cliente_id IN " +
                    "(SELECT id FROM cliente WHERE tenant_id = ? AND email = 'p1@test.com')",
                    TENANT_ACME, TENANT_ACME);
        jdbc.update("DELETE FROM cliente_identity_provider WHERE provider_user_id = ?", SUB);
        jdbc.update("DELETE FROM cliente WHERE tenant_id = ? AND email = 'p1@test.com'", TENANT_ACME);
        jdbc.update("DELETE FROM cliente WHERE tenant_id = ? AND documento = '111.222.333-44'", TENANT_ACME);
    }

    private RequestPostProcessor cliente() {
        return jwt().jwt(j -> j.subject(SUB)
                .claim("name", "Cliente P1")
                .claim("email", "p1@test.com")
                .claim("email_verified", true))
            .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject(USER_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"));
    }

    private String criarPayload() {
        LocalDateTime inicio = LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);
        return """
            {"lojaSlug":"acme","modeloId":"%s","dataInicio":"%s","dataFimPrevista":"%s",
             "pagamentoTipo":"SINAL","observacoes":"2 pessoas"}
            """.formatted(MODELO_ID, ISO.format(inicio), ISO.format(inicio.plusHours(2)));
    }

    private String criarReserva() throws Exception {
        MvcResult res = mockMvc.perform(post("/v1/customers/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(criarPayload())
                .with(cliente()))
            .andExpect(status().isCreated())
            .andReturn();
        return com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    // ============================ Criação ============================

    @Test
    @DisplayName("1º contato cria Cliente (PORTAL/ATIVA) + vínculo e calcula PIX do sinal (30%)")
    void testCriarPrimeiroContato() throws Exception {
        mockMvc.perform(post("/v1/customers/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(criarPayload())
                .with(cliente()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.lojaSlug").value("acme"))
            .andExpect(jsonPath("$.status").value("PENDENTE"))
            .andExpect(jsonPath("$.pagamento.status").value("AGUARDANDO"))
            // 2h × R$200 = R$400; sinal 30% = R$120
            .andExpect(jsonPath("$.pagamento.valorTotal").value(400.00))
            .andExpect(jsonPath("$.pagamento.valorSinal").value(120.00))
            .andExpect(jsonPath("$.pagamento.pixChave").value("pix@acme.com.br"))
            .andExpect(jsonPath("$.pagamento.pixCopiaEColaSinal",
                org.hamcrest.Matchers.containsString("5406120.00")));

        Integer clientes = jdbc.queryForObject(
            "SELECT count(*) FROM cliente WHERE tenant_id = ? AND email = 'p1@test.com' " +
            "AND origem = 'PORTAL' AND status_conta = 'ATIVA'", Integer.class, TENANT_ACME);
        assertThat(clientes).isEqualTo(1);
        String canal = jdbc.queryForObject(
            "SELECT canal FROM reserva WHERE tenant_id = ? AND canal = 'PORTAL' LIMIT 1",
            String.class, TENANT_ACME);
        assertThat(canal).isEqualTo("PORTAL");
    }

    @Test
    @DisplayName("2ª reserva reutiliza o mesmo Cliente (não duplica)")
    void testSegundaReservaReusaCliente() throws Exception {
        criarReserva();
        criarReserva();
        Integer clientes = jdbc.queryForObject(
            "SELECT count(*) FROM cliente WHERE tenant_id = ? AND email = 'p1@test.com'",
            Integer.class, TENANT_ACME);
        assertThat(clientes).isEqualTo(1);
    }

    @Test
    @DisplayName("CPF já cadastrado na loja (sem vínculo) bloqueia com orientação de claim")
    void testDedupeCpf() throws Exception {
        jdbc.update("""
            INSERT INTO cliente (id, tenant_id, nome, documento, origem, status_conta, ativo)
            VALUES (gen_random_uuid(), ?, 'Pré-existente Balcão', '111.222.333-44', 'BALCAO', 'PRE_CONTA', TRUE)
            """, TENANT_ACME);

        LocalDateTime inicio = LocalDateTime.now().plusDays(4).withHour(9).withMinute(0).withSecond(0).withNano(0);
        String payload = """
            {"lojaSlug":"acme","modeloId":"%s","dataInicio":"%s","dataFimPrevista":"%s",
             "pagamentoTipo":"SINAL","cpf":"111.222.333-44"}
            """.formatted(MODELO_ID, ISO.format(inicio), ISO.format(inicio.plusHours(1)));

        mockMvc.perform(post("/v1/customers/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(cliente()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message",
                org.hamcrest.Matchers.containsString("link de ativação")));
    }

    // ============================ Consultas ============================

    @Test
    @DisplayName("Minhas reservas e detalhe funcionam sem X-Tenant-Id")
    void testMinhasReservas() throws Exception {
        String id = criarReserva();

        mockMvc.perform(get("/v1/customers/reservas").with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(id));

        mockMvc.perform(get("/v1/customers/reservas/{id}", id).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modeloNome").value("GTX Portal 170"));

        // outro cliente não enxerga
        mockMvc.perform(get("/v1/customers/reservas/{id}", id)
                .with(jwt().jwt(j -> j.subject("outro-sub"))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
            .andExpect(status().isNotFound());
    }

    // ============================ Comprovante + fila staff ============================

    @Test
    @DisplayName("Comprovante → EM_ANALISE → aparece na fila staff → confirmação fecha checklist")
    void testComprovanteEFilaStaff() throws Exception {
        String id = criarReserva();
        String png = Base64.getEncoder().encodeToString(new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});

        mockMvc.perform(post("/v1/customers/reservas/{id}/comprovante", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tipo":"SINAL","valorInformado":120.00,"contentType":"image/png","dataBase64":"%s"}
                    """.formatted(png))
                .with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagamento.status").value("EM_ANALISE"));

        // fila staff
        mockMvc.perform(get("/v1/tenants/{t}/reservas/pagamentos-pendentes", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].reservaId").value(id))
            .andExpect(jsonPath("$[0].valorInformado").value(120.00))
            .andExpect(jsonPath("$[0].canal").value("PORTAL"));

        // comprovantes com URL de download
        mockMvc.perform(get("/v1/tenants/{t}/reservas/{id}/comprovantes", TENANT_ACME, id)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].downloadUrl").isNotEmpty());

        // staff confirma → checklist do cliente reflete
        mockMvc.perform(post("/v1/tenants/{t}/reservas/{id}/confirmar-sinal", TENANT_ACME, id)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorSinal\":120.00,\"tipo\":\"SINAL\"}")
                .with(admin()))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v1/customers/reservas/{id}/checklist", id).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagamentoOk").value(true))
            .andExpect(jsonPath("$.emailVerified").value(true))
            .andExpect(jsonPath("$.garantida").value(true))
            .andExpect(jsonPath("$.habilitacaoOk").value(false))
            .andExpect(jsonPath("$.prontaParaCheckin").value(false));
    }

    // ============================ Balcão × Portal (pagamento presencial) ============================

    @Test
    @DisplayName("Reserva de BALCÃO sem pagamento aparece como PRESENCIAL (pagamento na loja); paga vira CONFIRMADO; NO_SHOW passa no DTO")
    void testReservaBalcaoPresencialNoDto() throws Exception {
        // 1ª reserva de portal cria o Cliente + vínculo com o sub
        String portalId = criarReserva();

        UUID clienteId = jdbc.queryForObject(
            "SELECT id FROM cliente WHERE tenant_id = ? AND email = 'p1@test.com'",
            UUID.class, TENANT_ACME);
        UUID balcaoId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista,
                                 status, canal)
            VALUES (?, ?, ?, ?, now() + interval '1 day', now() + interval '1 day' + interval '2 hours',
                    'CONFIRMADA', 'BALCAO')
            """, balcaoId, TENANT_ACME, MODELO_ID, clienteId);

        // Balcão não pago → PRESENCIAL (apresentação: "pagamento na loja")
        mockMvc.perform(get("/v1/customers/reservas/{id}", balcaoId).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagamento.status").value("PRESENCIAL"));
        mockMvc.perform(get("/v1/customers/reservas/{id}/checklist", balcaoId).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagamentoStatus").value("PRESENCIAL"))
            .andExpect(jsonPath("$.pagamentoOk").value(false));

        // Reserva de PORTAL não é afetada — segue AGUARDANDO
        mockMvc.perform(get("/v1/customers/reservas/{id}", portalId).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagamento.status").value("AGUARDANDO"));

        // Pagamento presencial registrado pelo staff → CONFIRMADO
        mockMvc.perform(post("/v1/tenants/{t}/reservas/{id}/registrar-pagamento", TENANT_ACME, balcaoId)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"forma\":\"DINHEIRO\",\"valor\":400.00}")
                .with(admin()))
            .andExpect(status().isOk());
        mockMvc.perform(get("/v1/customers/reservas/{id}", balcaoId).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagamento.status").value("CONFIRMADO"));

        // NO_SHOW visível para o cliente
        UUID noShowId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista,
                                 status, canal)
            VALUES (?, ?, ?, ?, now() - interval '2 hours', now() - interval '1 hour',
                    'NO_SHOW', 'BALCAO')
            """, noShowId, TENANT_ACME, MODELO_ID, clienteId);
        mockMvc.perform(get("/v1/customers/reservas/{id}", noShowId).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    // ============================ Expiração ============================

    @Test
    @DisplayName("Job expira pré-reserva PORTAL sem pagamento após o prazo; EM_ANALISE não expira")
    void testExpiracao() throws Exception {
        String idAguardando = criarReserva();
        String idEmAnalise = criarReserva();
        String png = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        mockMvc.perform(post("/v1/customers/reservas/{id}/comprovante", idEmAnalise)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tipo":"TOTAL","valorInformado":400.00,"contentType":"image/png","dataBase64":"%s"}
                    """.formatted(png))
                .with(cliente()))
            .andExpect(status().isOk());

        jdbc.update("UPDATE reserva SET created_at = now() - interval '25 hours' WHERE id IN (?::uuid, ?::uuid)",
            idAguardando, idEmAnalise);

        int expiradas = customerReservaService.expirarPreReservasPortal(24);
        assertThat(expiradas).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT status FROM reserva WHERE id = ?::uuid",
            String.class, idAguardando)).isEqualTo("EXPIRADA");
        assertThat(jdbc.queryForObject("SELECT status FROM reserva WHERE id = ?::uuid",
            String.class, idEmAnalise)).isEqualTo("PENDENTE");
    }

    // ============================ Público por loja ============================

    @Test
    @DisplayName("Vitrine pública por loja expõe modelos com tenantId/slug")
    void testVitrinePublica() throws Exception {
        mockMvc.perform(get("/v1/public/lojas/acme/modelos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '%s')].lojaSlug".formatted(MODELO_ID),
                org.hamcrest.Matchers.contains("acme")));

        mockMvc.perform(get("/v1/public/lojas/acme"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("acme"));

        mockMvc.perform(get("/v1/public/lojas/nao-existe"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Disponibilidade pública responde agregada sem autenticação")
    void testDisponibilidadePublica() throws Exception {
        LocalDateTime inicio = LocalDateTime.now().plusDays(5).withHour(10).withMinute(0).withSecond(0).withNano(0);
        mockMvc.perform(get("/v1/public/lojas/acme/disponibilidade")
                .param("modeloId", MODELO_ID.toString())
                .param("dataInicio", ISO.format(inicio))
                .param("dataFimPrevista", ISO.format(inicio.plusHours(2))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalJetskis").value(1))
            .andExpect(jsonPath("$.aceitaComSinal").value(true));
    }

    @Test
    @DisplayName("Reserva é por MODELO: sem controle de estoque (default), cria mesmo sem jetski DISPONIVEL")
    void testReservaSemControleDeEstoque() throws Exception {
        // todos os jetskis do modelo ficam em manutenção
        jdbc.update("UPDATE jetski SET status = 'MANUTENCAO' WHERE modelo_id = ?", MODELO_ID);
        jdbc.update("UPDATE reserva_config SET controlar_estoque = false WHERE tenant_id = ?", TENANT_ACME);
        try {
            java.time.LocalDateTime inicio = java.time.LocalDateTime.now()
                .plusDays(6).withHour(9).withMinute(0).withSecond(0).withNano(0);
            mockMvc.perform(post("/v1/customers/reservas")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("""
                        {"lojaSlug":"acme","modeloId":"%s","dataInicio":"%s","dataFimPrevista":"%s","pagamentoTipo":"SINAL"}
                        """.formatted(MODELO_ID,
                            java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(inicio),
                            java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(inicio.plusHours(1))))
                    .with(cliente()))
                .andExpect(status().isCreated());

            // ligando o controle, o mesmo cenário bloqueia
            jdbc.update("UPDATE reserva_config SET controlar_estoque = true WHERE tenant_id = ?", TENANT_ACME);
            mockMvc.perform(post("/v1/customers/reservas")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("""
                        {"lojaSlug":"acme","modeloId":"%s","dataInicio":"%s","dataFimPrevista":"%s","pagamentoTipo":"SINAL"}
                        """.formatted(MODELO_ID,
                            java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(inicio.plusHours(3)),
                            java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(inicio.plusHours(4))))
                    .with(cliente()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message",
                    org.hamcrest.Matchers.containsString("Nenhum jetski disponível")));
        } finally {
            jdbc.update("UPDATE jetski SET status = 'DISPONIVEL' WHERE modelo_id = ?", MODELO_ID);
            jdbc.update("UPDATE reserva_config SET controlar_estoque = false WHERE tenant_id = ?", TENANT_ACME);
        }
    }

    @Test
    @DisplayName("Triagem na reserva: possuiCha define a via da habilitação (CHA sem GRU / EMA com)")
    void testTriagemChaNaReserva() throws Exception {
        java.time.LocalDateTime inicio = java.time.LocalDateTime.now()
            .plusDays(7).withHour(9).withMinute(0).withSecond(0).withNano(0);
        var iso = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        // possuiCha = true → via CHA
        String r1 = mockMvc.perform(post("/v1/customers/reservas")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                    {"lojaSlug":"acme","modeloId":"%s","dataInicio":"%s","dataFimPrevista":"%s",
                     "pagamentoTipo":"SINAL","possuiCha":true}
                    """.formatted(MODELO_ID, iso.format(inicio), iso.format(inicio.plusHours(1))))
                .with(cliente()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String id1 = com.jayway.jsonpath.JsonPath.read(r1, "$.id");
        assertThat(jdbc.queryForObject(
            "SELECT via FROM reserva_habilitacao WHERE reserva_id = ?::uuid", String.class, id1))
            .isEqualTo("CHA");

        // possuiCha = false → via EMA
        String r2 = mockMvc.perform(post("/v1/customers/reservas")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                    {"lojaSlug":"acme","modeloId":"%s","dataInicio":"%s","dataFimPrevista":"%s",
                     "pagamentoTipo":"SINAL","possuiCha":false}
                    """.formatted(MODELO_ID, iso.format(inicio.plusHours(3)), iso.format(inicio.plusHours(4))))
                .with(cliente()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String id2 = com.jayway.jsonpath.JsonPath.read(r2, "$.id");
        assertThat(jdbc.queryForObject(
            "SELECT via FROM reserva_habilitacao WHERE reserva_id = ?::uuid", String.class, id2))
            .isEqualTo("EMA");
    }
}
