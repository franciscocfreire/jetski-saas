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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P3 do Portal do Cliente: caminho B (CHA-MTA-E) self-service — dados
 * pessoais, anexos, videoaula/declarações e GRU (verificação DEMO-PAGO +
 * comprovante manual). A geração real da GRU (RPA Marinha) não roda em teste.
 */
@AutoConfigureMockMvc
@DisplayName("Portal do Cliente — P3 (EMA/GRU)")
class CustomerEmaIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @MockBean OPAAuthorizationService opa;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID MODELO_ID = UUID.fromString("77777777-7777-4777-8777-000000000021");
    private static final UUID JETSKI_ID = UUID.fromString("77777777-7777-4777-8777-000000000022");
    private static final String SUB = "ffffffff-0000-0000-0000-000000000001";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // PNG 1×1 real (o comprovante manual converte imagem→PDF e valida o header)
    private static final String PNG_B64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());

        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'P3 Modelo', 'Yamaha', 110, 2, 100.00, 5, 50.00, 300.00, FALSE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, MODELO_ID, TENANT_ACME);
        jdbc.update("""
            INSERT INTO jetski (id, tenant_id, modelo_id, serie, ano, horimetro_atual, status, ativo)
            VALUES (?, ?, ?, 'JET-P3-1', 2024, 3.0, 'DISPONIVEL', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, JETSKI_ID, TENANT_ACME, MODELO_ID);

        jdbc.update("DELETE FROM reserva_habilitacao WHERE tenant_id = ? AND reserva_id IN " +
                    "(SELECT id FROM reserva WHERE canal = 'PORTAL')", TENANT_ACME);
        jdbc.update("DELETE FROM reserva WHERE tenant_id = ? AND canal = 'PORTAL'", TENANT_ACME);
        jdbc.update("DELETE FROM cliente_anexo WHERE tenant_id = ? AND cliente_id IN " +
                    "(SELECT id FROM cliente WHERE email = 'p3@test.com')", TENANT_ACME);
        jdbc.update("DELETE FROM cliente_identity_provider WHERE provider_user_id = ?", SUB);
        jdbc.update("DELETE FROM cliente WHERE tenant_id = ? AND email = 'p3@test.com'", TENANT_ACME);
        jdbc.update("DELETE FROM cliente WHERE tenant_id = ? AND documento = '999.888.777-66'", TENANT_ACME);
    }

    private RequestPostProcessor cliente() {
        return jwt().jwt(j -> j.subject(SUB)
                .claim("name", "Cliente P3")
                .claim("email", "p3@test.com")
                .claim("email_verified", true))
            .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private String criarReserva() throws Exception {
        LocalDateTime inicio = LocalDateTime.now().plusDays(6).withHour(9).withMinute(0).withSecond(0).withNano(0);
        MvcResult res = mockMvc.perform(post("/v1/customers/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"lojaSlug":"acme","modeloId":"%s","dataInicio":"%s","dataFimPrevista":"%s","pagamentoTipo":"SINAL"}
                    """.formatted(MODELO_ID, ISO.format(inicio), ISO.format(inicio.plusHours(1))))
                .with(cliente()))
            .andExpect(status().isCreated())
            .andReturn();
        return com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    @Test
    @DisplayName("Dados pessoais: completa → completos=true; CPF define mas não troca")
    void testDadosPessoais() throws Exception {
        String id = criarReserva();

        mockMvc.perform(get("/v1/customers/reservas/{id}/ema/dados", id).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completos").value(false));

        mockMvc.perform(put("/v1/customers/reservas/{id}/ema/dados", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"cpf":"999.888.777-66","rg":"12.345.678-9","orgaoEmissor":"SSP/SC",
                     "nacionalidade":"Brasileira","naturalidade":"Florianópolis/SC",
                     "dataNascimento":"1995-05-20",
                     "endereco":{"cep":"88010-000","logradouro":"Rua do Mar","numero":"100",
                                 "complemento":"","bairro":"Centro","cidade":"Florianópolis","uf":"SC"}}
                    """)
                .with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completos").value(true))
            .andExpect(jsonPath("$.endereco.cep").value("88010-000"));

        // trocar CPF é bloqueado
        mockMvc.perform(put("/v1/customers/reservas/{id}/ema/dados", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cpf\":\"111.111.111-11\"}")
                .with(cliente()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message",
                org.hamcrest.Matchers.containsString("não pode ser alterado")));
    }

    @Test
    @DisplayName("Anexos: IDENTIDADE/SELFIE ok; CHA não é permitido por aqui")
    void testAnexos() throws Exception {
        String id = criarReserva();

        mockMvc.perform(post("/v1/customers/reservas/{id}/ema/anexos", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tipo":"IDENTIDADE","conteudoBase64":"data:image/png;base64,%s"}
                    """.formatted(PNG_B64))
                .with(cliente()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/v1/customers/reservas/{id}/ema/anexos", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tipo":"SELFIE","conteudoBase64":"data:image/png;base64,%s"}
                    """.formatted(PNG_B64))
                .with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItems("IDENTIDADE", "SELFIE")));

        mockMvc.perform(post("/v1/customers/reservas/{id}/ema/anexos", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tipo":"CHA","conteudoBase64":"data:image/png;base64,%s"}
                    """.formatted(PNG_B64))
                .with(cliente()))
            .andExpect(status().isBadRequest());

        // Preview: o cliente vê a imagem que enviou (GET /anexos/{tipo})
        mockMvc.perform(get("/v1/customers/reservas/{id}/ema/anexos/IDENTIDADE", id)
                .with(cliente()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"));

        // Tipo ainda não anexado → 404
        mockMvc.perform(get("/v1/customers/reservas/{id}/ema/anexos/COMPROVANTE_RESIDENCIA", id)
                .with(cliente()))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Videoaula + declarações via PUT ema; GRU não paga mantém resolvida=false")
    void testFlagsEma() throws Exception {
        String id = criarReserva();

        mockMvc.perform(put("/v1/customers/reservas/{id}/ema", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"videoaulaAssistida":true,"anexoSaude":true,"anexoRegras":true,
                     "anexoResidencia":true,"usaLentes":true,"usaAparelho":false}
                    """)
                .with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.videoaulaAssistida").value(true))
            .andExpect(jsonPath("$.anexoSaude").value(true))
            .andExpect(jsonPath("$.anexoRegras").value(true))
            .andExpect(jsonPath("$.usaLentes").value(true))
            .andExpect(jsonPath("$.resolvida").value(false));
    }

    @Test
    @DisplayName("GRU DEMO-PAGO: verificar → pago, resolvida e checklist habilitacaoOk")
    void testGruVerificacaoDemo() throws Exception {
        String id = criarReserva();

        // registra via EMA e injeta sessão DEMO (hook de dev do GruClient)
        mockMvc.perform(put("/v1/customers/reservas/{id}/ema", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"videoaulaAssistida\":true}")
                .with(cliente()))
            .andExpect(status().isOk());
        jdbc.update("UPDATE reserva_habilitacao SET gru_id_sessao = 'DEMO-PAGO-P3', " +
                    "gru_pix_copia_e_cola = 'pix-demo' WHERE reserva_id = ?::uuid", id);

        mockMvc.perform(post("/v1/customers/reservas/{id}/ema/gru/verificar", id).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pago").value(true))
            .andExpect(jsonPath("$.situacao").value("CONCLUIDO"));

        mockMvc.perform(get("/v1/customers/reservas/{id}/ema", id).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gru.pago").value(true))
            .andExpect(jsonPath("$.resolvida").value(true));

        mockMvc.perform(get("/v1/customers/reservas/{id}/checklist", id).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.habilitacaoOk").value(true));
    }

    @Test
    @DisplayName("Comprovante manual da GRU marca pago + resolvida")
    void testComprovanteManual() throws Exception {
        String id = criarReserva();
        mockMvc.perform(put("/v1/customers/reservas/{id}/ema", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"anexoRegras\":true}")
                .with(cliente()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/v1/customers/reservas/{id}/ema/gru/comprovante", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"conteudoBase64":"data:image/png;base64,%s"}
                    """.formatted(PNG_B64))
                .with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gru.pago").value(true))
            .andExpect(jsonPath("$.gru.comprovanteDisponivel").value(true))
            .andExpect(jsonPath("$.resolvida").value(true));

        Boolean resolvida = jdbc.queryForObject(
            "SELECT resolvida FROM reserva_habilitacao WHERE reserva_id = ?::uuid", Boolean.class, id);
        assertThat(resolvida).isTrue();
    }
}
