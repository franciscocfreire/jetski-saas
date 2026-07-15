package com.jetski.locacoes;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.security.FederatedIdentity;
import com.jetski.shared.security.UserProvisioningService;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unificação de contas por CPF (portal): colisão 409 estruturado, elegibilidade
 * do merge (só duplicata de origem Google, sem vínculos de loja), OTP no Redis
 * com limite de tentativas, execução do merge (transfer + descarte + auditoria
 * global).
 */
@AutoConfigureMockMvc
@DisplayName("Portal do Cliente — Merge de contas por CPF")
class CustomerCpfMergeIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;

    @MockBean OPAAuthorizationService opa;
    @MockBean UserProvisioningService provisioning;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final String SUB_GOOGLE = "dgdgdgdg-0000-0000-0000-000000000001";
    private static final String SUB_OWNER = "dgdgdgdg-0000-0000-0000-000000000002";
    private static final String CPF_OWNER = "111.444.777-35";
    private static final String CPF_OWNER_DIGITS = "11144477735";
    private static final FederatedIdentity FED_GOOGLE =
        new FederatedIdentity("google", "g-123", "pessoa@gmail.com");

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());
        when(provisioning.definirCpf(anyString(), anyString())).thenReturn(true);
        when(provisioning.updateUserName(anyString(), anyString())).thenReturn(true);

        jdbc.update("DELETE FROM cliente_identity_provider WHERE provider_user_id IN (?, ?)",
            SUB_GOOGLE, SUB_OWNER);
        jdbc.update("DELETE FROM cliente WHERE email IN ('merge-dup@test.com', 'merge-owner@test.com')");
        jdbc.update("DELETE FROM customer_profile WHERE provider_user_id IN (?, ?)",
            SUB_GOOGLE, SUB_OWNER);
        jdbc.update("DELETE FROM auditoria WHERE acao = 'CONTA_CPF_MERGE'");
        redis.delete(redis.keys("otp:cpfmerge:*"));

        // conta dona do CPF (perfil global já com CPF definido)
        jdbc.update("""
            INSERT INTO customer_profile (provider, provider_user_id, nome, cpf)
            VALUES ('keycloak', ?, 'Dona do CPF', ?)
            """, SUB_OWNER, CPF_OWNER);
    }

    private RequestPostProcessor cliente(String sub, String email) {
        return jwt().jwt(j -> j.subject(sub)
                .claim("name", "Cliente Google")
                .claim("email", email)
                .claim("email_verified", true))
            .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private void vincularLojaAcme(String sub, String email) {
        UUID clienteId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO cliente (id, tenant_id, nome, email, origem, status_conta, ativo)
            VALUES (?, ?, 'Cliente Google', ?, 'PORTAL', 'ATIVA', TRUE)
            """, clienteId, TENANT_ACME, email);
        jdbc.update("""
            INSERT INTO cliente_identity_provider (tenant_id, cliente_id, provider, provider_user_id)
            VALUES (?, ?, 'keycloak', ?)
            """, TENANT_ACME, clienteId, sub);
    }

    private String codigoNoRedis() {
        String guardado = redis.opsForValue().get("otp:cpfmerge:code:" + SUB_GOOGLE);
        assertThat(guardado).isNotNull();
        return guardado.split("\\|", 3)[0];
    }

    private void enviarElegivel() throws Exception {
        when(provisioning.findFederatedIdentity(SUB_GOOGLE, "google")).thenReturn(FED_GOOGLE);
        when(provisioning.findEmailById(SUB_OWNER)).thenReturn("dono@example.com");

        mockMvc.perform(post("/v1/customers/self/cpf-merge/enviar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cpf\":\"" + CPF_OWNER + "\"}")
                .with(cliente(SUB_GOOGLE, "merge-dup@test.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.disponivel").value(true))
            .andExpect(jsonPath("$.emailMascarado").value("do***@example.com"));
    }

    @Test
    @DisplayName("PUT self com CPF de outra conta → 409 CPF_EM_USO (mesmo com máscara diferente)")
    void testColisaoCpf409() throws Exception {
        // sem máscara — o dono salvou COM máscara (normalização fecha o buraco)
        mockMvc.perform(put("/v1/customers/self")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Cliente Google\",\"cpf\":\"" + CPF_OWNER_DIGITS + "\"}")
                .with(cliente(SUB_GOOGLE, "merge-dup@test.com")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.details.code").value("CPF_EM_USO"))
            .andExpect(jsonPath("$.details.mergeDisponivel").value(true));
    }

    @Test
    @DisplayName("enviar elegível → OTP no Redis + e-mail mascarado do dono")
    void testEnviarElegivel() throws Exception {
        enviarElegivel();
        assertThat(redis.opsForValue().get("otp:cpfmerge:code:" + SUB_GOOGLE))
            .contains("|" + SUB_OWNER + "|" + CPF_OWNER_DIGITS);
    }

    @Test
    @DisplayName("enviar sem identidade Google → SEM_IDENTIDADE_GOOGLE (sem OTP)")
    void testEnviarSemGoogle() throws Exception {
        when(provisioning.findFederatedIdentity(SUB_GOOGLE, "google")).thenReturn(null);

        mockMvc.perform(post("/v1/customers/self/cpf-merge/enviar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cpf\":\"" + CPF_OWNER + "\"}")
                .with(cliente(SUB_GOOGLE, "merge-dup@test.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.disponivel").value(false))
            .andExpect(jsonPath("$.motivo").value("SEM_IDENTIDADE_GOOGLE"));

        assertThat(redis.opsForValue().get("otp:cpfmerge:code:" + SUB_GOOGLE)).isNull();
    }

    @Test
    @DisplayName("enviar com vínculo de loja na conta atual → CONTA_COM_VINCULOS")
    void testEnviarContaComVinculos() throws Exception {
        vincularLojaAcme(SUB_GOOGLE, "merge-dup@test.com");

        mockMvc.perform(post("/v1/customers/self/cpf-merge/enviar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cpf\":\"" + CPF_OWNER + "\"}")
                .with(cliente(SUB_GOOGLE, "merge-dup@test.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.disponivel").value(false))
            .andExpect(jsonPath("$.motivo").value("CONTA_COM_VINCULOS"));
    }

    @Test
    @DisplayName("enviar com CPF livre → CPF_LIVRE (orienta salvar no perfil)")
    void testEnviarCpfLivre() throws Exception {
        when(provisioning.findUserIdByUsername(anyString())).thenReturn(null);

        mockMvc.perform(post("/v1/customers/self/cpf-merge/enviar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cpf\":\"529.982.247-25\"}")
                .with(cliente(SUB_GOOGLE, "merge-dup@test.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.disponivel").value(false))
            .andExpect(jsonPath("$.motivo").value("CPF_LIVRE"));
    }

    @Test
    @DisplayName("verificar: código errado repetido → 400 'Muitas tentativas'")
    void testVerificarMuitasTentativas() throws Exception {
        enviarElegivel();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/v1/customers/self/cpf-merge/verificar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cpf\":\"" + CPF_OWNER + "\",\"codigo\":\"000000\"}")
                    .with(cliente(SUB_GOOGLE, "merge-dup@test.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificado").value(false));
        }
        mockMvc.perform(post("/v1/customers/self/cpf-merge/verificar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cpf\":\"" + CPF_OWNER + "\",\"codigo\":\"000000\"}")
                .with(cliente(SUB_GOOGLE, "merge-dup@test.com")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message",
                org.hamcrest.Matchers.containsString("Muitas tentativas")));
    }

    @Test
    @DisplayName("verificar com código certo → merge (transfer + descarte + auditoria global)")
    void testMergeCompleto() throws Exception {
        enviarElegivel();
        when(provisioning.transferFederatedIdentity(SUB_GOOGLE, SUB_OWNER, "google")).thenReturn(true);
        when(provisioning.deleteUser(SUB_GOOGLE)).thenReturn(true);

        mockMvc.perform(post("/v1/customers/self/cpf-merge/verificar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cpf\":\"" + CPF_OWNER + "\",\"codigo\":\"" + codigoNoRedis() + "\"}")
                .with(cliente(SUB_GOOGLE, "merge-dup@test.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificado").value(true))
            .andExpect(jsonPath("$.mergeConcluido").value(true));

        verify(provisioning).transferFederatedIdentity(SUB_GOOGLE, SUB_OWNER, "google");
        verify(provisioning).deleteUser(SUB_GOOGLE);

        // perfil global da duplicata descartado (criado no obter() do enviar)
        Integer dupProfiles = jdbc.queryForObject(
            "SELECT count(*) FROM customer_profile WHERE provider_user_id = ?",
            Integer.class, SUB_GOOGLE);
        assertThat(dupProfiles).isZero();

        // OTP consumido
        assertThat(redis.opsForValue().get("otp:cpfmerge:code:" + SUB_GOOGLE)).isNull();

        // trilha global (tenant_id NULL — policy V051 permite o INSERT)
        var audit = jdbc.queryForMap(
            "SELECT tenant_id, dados_novos::text AS d FROM auditoria WHERE acao = 'CONTA_CPF_MERGE'");
        assertThat(audit.get("tenant_id")).isNull();
        assertThat((String) audit.get("d")).contains(SUB_OWNER).contains(SUB_GOOGLE)
            .doesNotContain(CPF_OWNER_DIGITS); // CPF só mascarado na trilha
    }

    @Test
    @DisplayName("reserva do portal com CPF de outra conta → 409 CPF_EM_USO")
    void testReservaComCpfDeOutraConta() throws Exception {
        // seed mínimo do marketplace (mesmo padrão do CustomerReservaIntegrationTest)
        UUID modeloId = UUID.fromString("77777777-7777-4777-8777-000000000091");
        UUID jetskiId = UUID.fromString("77777777-7777-4777-8777-000000000092");
        jdbc.update("UPDATE tenant SET exibir_no_marketplace = true, pix_chave = 'pix@acme.com.br' " +
                    "WHERE id = ?", TENANT_ACME);
        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo, exibir_no_marketplace)
            VALUES (?, ?, 'GTX Merge 170', 'Sea-Doo', 170, 2, 200.00, 5, 50.00, 300.00, FALSE, TRUE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, modeloId, TENANT_ACME);
        jdbc.update("""
            INSERT INTO jetski (id, tenant_id, modelo_id, serie, ano, horimetro_atual, status, ativo)
            VALUES (?, ?, ?, 'JET-MERGE-1', 2024, 10.0, 'DISPONIVEL', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, jetskiId, TENANT_ACME, modeloId);

        java.time.LocalDateTime inicio = java.time.LocalDateTime.now()
            .plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);
        var iso = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        mockMvc.perform(post("/v1/customers/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"lojaSlug":"acme","modeloId":"%s","dataInicio":"%s","dataFimPrevista":"%s",
                     "pagamentoTipo":"SINAL","cpf":"%s","telefone":"48999990000"}
                    """.formatted(modeloId, iso.format(inicio), iso.format(inicio.plusHours(1)), CPF_OWNER))
                .with(cliente(SUB_GOOGLE, "merge-dup@test.com")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.details.code").value("CPF_EM_USO"));
    }
}
