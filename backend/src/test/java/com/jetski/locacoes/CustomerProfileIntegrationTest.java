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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Identidade GLOBAL do cliente (customer_profile): backfill lazy dos vínculos,
 * CPF define-only + único entre contas + sincronizado c/ Keycloak (username),
 * hidratação do Cliente em nova loja e guarda "não reserve para outro CPF".
 */
@AutoConfigureMockMvc
@DisplayName("Portal do Cliente — Identidade global")
class CustomerProfileIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @MockBean OPAAuthorizationService opa;
    @MockBean UserProvisioningService provisioning;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID TENANT_MARINA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID MODELO_MARINA = UUID.fromString("77777777-7777-4777-8777-000000000041");
    private static final UUID JETSKI_MARINA = UUID.fromString("77777777-7777-4777-8777-000000000042");
    private static final String SUB = "cdcdcdcd-0000-0000-0000-000000000001";
    private static final String SUB2 = "cdcdcdcd-0000-0000-0000-000000000002";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());
        when(provisioning.definirCpf(anyString(), anyString())).thenReturn(true);
        when(provisioning.updateUserName(anyString(), anyString())).thenReturn(true);

        // loja 2 (marina-bay) com modelo/jetski para a reserva multi-loja
        jdbc.update("UPDATE tenant SET status = 'ATIVO' WHERE id = ?", TENANT_MARINA);
        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'Marina Modelo', 'Yamaha', 110, 2, 100.00, 5, 50.00, 300.00, FALSE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, MODELO_MARINA, TENANT_MARINA);
        jdbc.update("""
            INSERT INTO jetski (id, tenant_id, modelo_id, serie, ano, horimetro_atual, status, ativo)
            VALUES (?, ?, ?, 'JET-MARINA-1', 2024, 1.0, 'DISPONIVEL', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, JETSKI_MARINA, TENANT_MARINA, MODELO_MARINA);

        jdbc.update("DELETE FROM customer_profile WHERE provider_user_id IN (?, ?)", SUB, SUB2);
        jdbc.update("DELETE FROM reserva WHERE tenant_id IN (?, ?) AND canal = 'PORTAL'",
            TENANT_ACME, TENANT_MARINA);
        jdbc.update("DELETE FROM cliente_identity_provider WHERE provider_user_id IN (?, ?)", SUB, SUB2);
        jdbc.update("DELETE FROM cliente WHERE email IN ('perfil@test.com', 'perfil2@test.com')");
    }

    private RequestPostProcessor cliente(String sub, String email) {
        return jwt().jwt(j -> j.subject(sub)
                .claim("name", "Cliente Perfil")
                .claim("email", email)
                .claim("email_verified", true))
            .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private void seedVinculoAcmeComIdentidade() {
        UUID clienteId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO cliente (id, tenant_id, nome, email, documento, rg, nacionalidade,
                                 naturalidade, origem, status_conta, ativo)
            VALUES (?, ?, 'Cliente Perfil', 'perfil@test.com', '321.654.987-00', 'RG-11',
                    'Brasileira', 'Floripa/SC', 'PORTAL', 'ATIVA', TRUE)
            """, clienteId, TENANT_ACME);
        jdbc.update("""
            INSERT INTO cliente_identity_provider (tenant_id, cliente_id, provider, provider_user_id)
            VALUES (?, ?, 'keycloak', ?)
            """, TENANT_ACME, clienteId, SUB);
    }

    @Test
    @DisplayName("Backfill lazy: GET self monta o perfil a partir do Cliente já vinculado")
    void testBackfillLazy() throws Exception {
        seedVinculoAcmeComIdentidade();

        mockMvc.perform(get("/v1/customers/self").with(cliente(SUB, "perfil@test.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.identidade.cpf").value("321.654.987-00"))
            .andExpect(jsonPath("$.identidade.rg").value("RG-11"))
            .andExpect(jsonPath("$.identidade.naturalidade").value("Floripa/SC"));

        // CPF sincronizado com o Keycloak (username = dígitos)
        verify(provisioning).definirCpf(SUB, "32165498700");
    }

    @Test
    @DisplayName("PUT self define CPF (uma vez) e bloqueia troca; unicidade entre contas")
    void testCpfDefineOnlyEUnico() throws Exception {
        mockMvc.perform(put("/v1/customers/self")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Cliente Perfil\",\"cpf\":\"111.222.333-96\",\"rg\":\"RG-9\"}")
                .with(cliente(SUB, "perfil@test.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cpf").value("111.222.333-96"));

        // trocar → 400
        mockMvc.perform(put("/v1/customers/self")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Cliente Perfil\",\"cpf\":\"999.999.999-99\"}")
                .with(cliente(SUB, "perfil@test.com")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message",
                org.hamcrest.Matchers.containsString("não pode ser alterado")));

        // outra conta com o MESMO CPF → 400 (unicidade global)
        mockMvc.perform(put("/v1/customers/self")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Outra Conta\",\"cpf\":\"111.222.333-96\"}")
                .with(cliente(SUB2, "perfil2@test.com")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message",
                org.hamcrest.Matchers.containsString("vinculado a outra conta")));
    }

    @Test
    @DisplayName("Reserva em SEGUNDA loja herda a identidade; endereço NÃO segue")
    void testSegundaLojaHerdaIdentidade() throws Exception {
        seedVinculoAcmeComIdentidade();
        // endereço só na loja acme
        jdbc.update("UPDATE cliente SET endereco = '{\"cep\":\"88010-000\"}'::jsonb " +
            "WHERE email = 'perfil@test.com' AND tenant_id = ?", TENANT_ACME);

        LocalDateTime inicio = LocalDateTime.now().plusDays(4).withHour(10).withMinute(0).withSecond(0).withNano(0);
        mockMvc.perform(post("/v1/customers/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"lojaSlug":"marina-bay","modeloId":"%s","dataInicio":"%s","dataFimPrevista":"%s","pagamentoTipo":"SINAL","telefone":"48999990000"}
                    """.formatted(MODELO_MARINA, ISO.format(inicio), ISO.format(inicio.plusHours(1))))
                .with(cliente(SUB, "perfil@test.com")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.lojaSlug").value("marina-bay"));

        // novo Cliente na marina-bay nasceu com CPF/RG do perfil e SEM endereço;
        // telefone/whats são POR LOJA (vieram do wizard desta reserva)
        var row = jdbc.queryForMap(
            "SELECT documento, rg, endereco, telefone, whatsapp FROM cliente " +
            "WHERE tenant_id = ? AND email = 'perfil@test.com'", TENANT_MARINA);
        assertThat(row.get("documento")).isEqualTo("321.654.987-00");
        assertThat(row.get("rg")).isEqualTo("RG-11");
        assertThat(row.get("endereco")).isNull();
        assertThat(row.get("telefone")).isEqualTo("48999990000");
        assertThat(row.get("whatsapp")).isEqualTo("48999990000");
    }

    @Test
    @DisplayName("Wizard com CPF divergente do cadastro é bloqueado")
    void testReservaComOutroCpf() throws Exception {
        seedVinculoAcmeComIdentidade();

        LocalDateTime inicio = LocalDateTime.now().plusDays(5).withHour(9).withMinute(0).withSecond(0).withNano(0);
        mockMvc.perform(post("/v1/customers/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"lojaSlug":"marina-bay","modeloId":"%s","dataInicio":"%s","dataFimPrevista":"%s",
                     "pagamentoTipo":"SINAL","cpf":"000.111.222-33"}
                    """.formatted(MODELO_MARINA, ISO.format(inicio), ISO.format(inicio.plusHours(1))))
                .with(cliente(SUB, "perfil@test.com")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message",
                org.hamcrest.Matchers.containsString("não é possível reservar para outro CPF")));
    }
}
