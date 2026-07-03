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
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * P2 do Portal do Cliente: termo assinado remotamente (aceite com origem
 * PORTAL, com/sem OTP) e habilitação caminho A (CHA + foto) pelo cliente.
 */
@AutoConfigureMockMvc
@DisplayName("Portal do Cliente — P2 (termos + habilitação)")
class CustomerDocumentacaoIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;

    @MockBean OPAAuthorizationService opa;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID MODELO_ID = UUID.fromString("77777777-7777-4777-8777-000000000011");
    private static final UUID JETSKI_ID = UUID.fromString("77777777-7777-4777-8777-000000000012");
    private static final String SUB = "eeeeeeee-0000-0000-0000-000000000001";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // PNG 1x1 válido
    private static final String PNG_B64 = Base64.getEncoder().encodeToString(new byte[]{
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4});

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());

        jdbc.update("UPDATE tenant SET assinatura_config = NULL WHERE id = ?", TENANT_ACME);
        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'P2 Modelo', 'Sea-Doo', 130, 2, 100.00, 5, 50.00, 300.00, FALSE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, MODELO_ID, TENANT_ACME);
        jdbc.update("""
            INSERT INTO jetski (id, tenant_id, modelo_id, serie, ano, horimetro_atual, status, ativo)
            VALUES (?, ?, ?, 'JET-P2-1', 2024, 5.0, 'DISPONIVEL', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, JETSKI_ID, TENANT_ACME, MODELO_ID);

        jdbc.update("DELETE FROM reserva_aceite WHERE tenant_id = ?", TENANT_ACME);
        jdbc.update("DELETE FROM reserva_habilitacao WHERE tenant_id = ?", TENANT_ACME);
        jdbc.update("DELETE FROM reserva WHERE tenant_id = ? AND canal = 'PORTAL'", TENANT_ACME);
        jdbc.update("DELETE FROM cliente_anexo WHERE tenant_id = ? AND cliente_id IN " +
                    "(SELECT id FROM cliente WHERE email = 'p2@test.com')", TENANT_ACME);
        jdbc.update("DELETE FROM cliente_identity_provider WHERE provider_user_id = ?", SUB);
        jdbc.update("DELETE FROM cliente WHERE tenant_id = ? AND email = 'p2@test.com'", TENANT_ACME);
    }

    private RequestPostProcessor cliente() {
        return jwt().jwt(j -> j.subject(SUB)
                .claim("name", "Cliente P2")
                .claim("email", "p2@test.com")
                .claim("email_verified", true))
            .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private String criarReserva() throws Exception {
        LocalDateTime inicio = LocalDateTime.now().plusDays(5).withHour(10).withMinute(0).withSecond(0).withNano(0);
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
    @DisplayName("Aceite remoto sem OTP: assina → origem PORTAL → checklist termosOk")
    void testAceiteSemOtp() throws Exception {
        String id = criarReserva();

        mockMvc.perform(get("/v1/customers/reservas/{id}/aceite/otp", id).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ativo").value(false));

        mockMvc.perform(post("/v1/customers/reservas/{id}/aceite", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assinaturaBase64\":\"data:image/png;base64,%s\"}".formatted(PNG_B64))
                .with(cliente()))
            .andExpect(status().isOk());

        String origem = jdbc.queryForObject(
            "SELECT origem FROM reserva_aceite WHERE reserva_id = ?::uuid", String.class, id);
        assertThat(origem).isEqualTo("PORTAL");

        mockMvc.perform(get("/v1/customers/reservas/{id}/checklist", id).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.termosOk").value(true));
    }

    @Test
    @DisplayName("Aceite com OTP exigido: bloqueia sem código; enviar → verificar → assina")
    void testAceiteComOtp() throws Exception {
        jdbc.update("UPDATE tenant SET assinatura_config = " +
            "'{\"otp\":{\"ativo\":true,\"canal\":\"EMAIL\"}}'::jsonb WHERE id = ?", TENANT_ACME);
        String id = criarReserva();

        // sem OTP verificado → bloqueia
        mockMvc.perform(post("/v1/customers/reservas/{id}/aceite", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assinaturaBase64\":\"%s\"}".formatted(PNG_B64))
                .with(cliente()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("OTP")));

        // envia e lê o código do Redis (e-mail em dev/test é log/Mailpit)
        mockMvc.perform(post("/v1/customers/reservas/{id}/aceite/otp/enviar", id).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ativo").value(true));
        String codigo = redis.opsForValue().get("otp:code:" + id);
        assertThat(codigo).isNotBlank();

        mockMvc.perform(post("/v1/customers/reservas/{id}/aceite/otp/verificar", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"%s\"}".formatted(codigo))
                .with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificado").value(true));

        mockMvc.perform(post("/v1/customers/reservas/{id}/aceite", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assinaturaBase64\":\"%s\"}".formatted(PNG_B64))
                .with(cliente()))
            .andExpect(status().isOk());

        Boolean otpVerificado = jdbc.queryForObject(
            "SELECT otp_verificado FROM reserva_aceite WHERE reserva_id = ?::uuid", Boolean.class, id);
        assertThat(otpVerificado).isTrue();
    }

    @Test
    @DisplayName("CHA pelo cliente: envia número+foto → resolvida + anexo CHA salvo")
    void testEnviarCha() throws Exception {
        String id = criarReserva();

        mockMvc.perform(get("/v1/customers/reservas/{id}/habilitacao", id).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resolvida").value(false));

        mockMvc.perform(post("/v1/customers/reservas/{id}/habilitacao/cha", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"categoria":"MOTONAUTA","numero":"CHA-123456","validade":"2030-01-01",
                     "fotoBase64":"data:image/png;base64,%s"}
                    """.formatted(PNG_B64))
                .with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resolvida").value(true))
            .andExpect(jsonPath("$.temFotoCha").value(true));

        Integer anexos = jdbc.queryForObject(
            "SELECT count(*) FROM cliente_anexo WHERE tipo = 'CHA' AND cliente_id IN " +
            "(SELECT id FROM cliente WHERE email = 'p2@test.com')", Integer.class);
        assertThat(anexos).isEqualTo(1);

        mockMvc.perform(get("/v1/customers/reservas/{id}/checklist", id).with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.habilitacaoOk").value(true));
    }

    @Test
    @DisplayName("CHA vencida é rejeitada com orientação da CHA-MTA-E")
    void testChaVencida() throws Exception {
        String id = criarReserva();
        mockMvc.perform(post("/v1/customers/reservas/{id}/habilitacao/cha", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoria\":\"ARRAIS\",\"numero\":\"X1\",\"validade\":\"2020-01-01\"}")
                .with(cliente()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("vencida")));
    }
}
