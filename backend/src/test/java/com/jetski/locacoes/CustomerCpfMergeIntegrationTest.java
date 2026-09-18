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
import static org.mockito.Mockito.never;
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
    // Pessoas (identidade única, F4) determinísticas do teste
    private static final UUID USUARIO_GOOGLE = UUID.fromString("dada0000-0000-0000-0000-0000000000aa");
    private static final UUID USUARIO_OWNER = UUID.fromString("dada0000-0000-0000-0000-0000000000ab");
    private static final UUID USUARIO_DUP = UUID.fromString("dada0000-0000-0000-0000-0000000000ac");
    private static final String CPF_OWNER_DIGITS = "11144477735";
    private static final FederatedIdentity FED_GOOGLE =
        new FederatedIdentity("google", "g-123", "pessoa@gmail.com");

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());
        when(provisioning.definirCpf(anyString(), anyString())).thenReturn(true);
        when(provisioning.updateUserName(anyString(), anyString())).thenReturn(true);
        // F4: o enviar() provisiona a PESSOA da duplicata via findEmailById(sub)
        when(provisioning.findEmailById(SUB_GOOGLE)).thenReturn("merge-dup@test.com");

        jdbc.update("DELETE FROM cliente WHERE email IN ('merge-dup@test.com', 'merge-owner@test.com')");
        jdbc.update("DELETE FROM customer_profile WHERE usuario_id IN "
            + "(SELECT usuario_id FROM usuario_identity_provider "
            + " WHERE provider = 'keycloak' AND provider_user_id IN (?, ?))",
            SUB_GOOGLE, SUB_OWNER);
        jdbc.update("DELETE FROM auditoria WHERE acao = 'CONTA_CPF_MERGE'");
        redis.delete(redis.keys("otp:cpfmerge:*"));

        // conta dona do CPF: pessoa global + mapping + perfil já com CPF definido
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo) VALUES (?, 'merge-owner@test.com', "
            + "'Dona do CPF', TRUE) ON CONFLICT DO NOTHING", USUARIO_OWNER);
        jdbc.update("INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id) "
            + "SELECT ?, 'keycloak', ? WHERE NOT EXISTS (SELECT 1 FROM usuario_identity_provider "
            + "WHERE provider = 'keycloak' AND provider_user_id = ?)", USUARIO_OWNER, SUB_OWNER, SUB_OWNER);
        jdbc.update("""
            INSERT INTO customer_profile (usuario_id, nome, cpf)
            SELECT usuario_id, 'Dona do CPF', ?
              FROM usuario_identity_provider
             WHERE provider = 'keycloak' AND provider_user_id = ?
            """, CPF_OWNER, SUB_OWNER);
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
        // Identidade única (F4): pessoa global + mapping do sub + ficha → pessoa
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo) VALUES (?, ?, 'Cliente Google', TRUE) "
            + "ON CONFLICT DO NOTHING", USUARIO_GOOGLE, email);
        jdbc.update("INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id) "
            + "SELECT ?, 'keycloak', ? WHERE NOT EXISTS (SELECT 1 FROM usuario_identity_provider "
            + "WHERE provider = 'keycloak' AND provider_user_id = ?)", USUARIO_GOOGLE, sub, sub);
        // sobras de outras classes com o MESMO sub/tenant violariam o unique (tenant_id, usuario_id)
        jdbc.update("UPDATE cliente SET usuario_id = NULL WHERE usuario_id = (SELECT usuario_id "
            + "FROM usuario_identity_provider WHERE provider = 'keycloak' AND provider_user_id = ?) "
            + "AND tenant_id = (SELECT tenant_id FROM cliente WHERE id = ?) AND id <> ?", sub, clienteId, clienteId);
        jdbc.update("UPDATE cliente SET usuario_id = (SELECT usuario_id FROM usuario_identity_provider "
            + "WHERE provider = 'keycloak' AND provider_user_id = ?) WHERE id = ?", sub, clienteId);
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
        UUID dupUsuario = usuarioDaDuplicata();

        mockMvc.perform(post("/v1/customers/self/cpf-merge/verificar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cpf\":\"" + CPF_OWNER + "\",\"codigo\":\"" + codigoNoRedis() + "\"}")
                .with(cliente(SUB_GOOGLE, "merge-dup@test.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificado").value(true))
            .andExpect(jsonPath("$.mergeConcluido").value(true));

        verify(provisioning).transferFederatedIdentity(SUB_GOOGLE, SUB_OWNER, "google");
        verify(provisioning).deleteUser(SUB_GOOGLE);

        // perfil global da duplicata descartado (criado no obter() do enviar);
        // a PESSOA da duplicata também morre (F3/D7) — mapping some junto, por
        // isso o usuario_id foi capturado ANTES (uma subquery pelo mapping seria vácua)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_profile WHERE usuario_id = ?",
            Integer.class, dupUsuario)).as("perfil da duplicata").isZero();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM usuario_identity_provider WHERE provider = 'keycloak' AND provider_user_id = ?",
            Integer.class, SUB_GOOGLE)).as("mapping da duplicata").isZero();

        // OTP consumido
        assertThat(redis.opsForValue().get("otp:cpfmerge:code:" + SUB_GOOGLE)).isNull();

        // trilha global (tenant_id NULL — policy V051 permite o INSERT)
        var audit = jdbc.queryForMap(
            "SELECT tenant_id, dados_novos::text AS d FROM auditoria WHERE acao = 'CONTA_CPF_MERGE'");
        assertThat(audit.get("tenant_id")).isNull();
        assertThat((String) audit.get("d")).contains(SUB_OWNER).contains(SUB_GOOGLE)
            .doesNotContain(CPF_OWNER_DIGITS); // CPF só mascarado na trilha
    }

    /** Pessoa da duplicata já existente (semeada por SQL): o obter() do enviar() só liga o perfil a ela,
     *  sem provisionar — logo sem o evento PESSOA_PROVISIONADA, cujo listener é @Async e escreveria a
     *  trilha em corrida com o teste. */
    private void semearPessoaDaDuplicata() {
        // Sobras de outros testes (pessoa provisionada pelo enviar(), COM trilha) trocariam o
        // ramo do descarte: apaga qualquer outra pessoa com este e-mail/sub antes de semear.
        jdbc.update("DELETE FROM customer_profile WHERE usuario_id IN "
            + "(SELECT id FROM usuario WHERE email = 'merge-dup@test.com' AND id <> ?)", USUARIO_DUP);
        jdbc.update("DELETE FROM usuario_identity_provider WHERE (provider = 'keycloak' AND provider_user_id = ? "
            + "AND usuario_id <> ?) OR usuario_id IN (SELECT id FROM usuario WHERE email = 'merge-dup@test.com' AND id <> ?)",
            SUB_GOOGLE, USUARIO_DUP, USUARIO_DUP);
        jdbc.update("DELETE FROM usuario WHERE email = 'merge-dup@test.com' AND id <> ?", USUARIO_DUP);
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo) VALUES (?, 'merge-dup@test.com', "
            + "'Duplicata', TRUE) ON CONFLICT DO NOTHING", USUARIO_DUP);
        jdbc.update("INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id) "
            + "SELECT ?, 'keycloak', ? WHERE NOT EXISTS "
            + "(SELECT 1 FROM usuario_identity_provider WHERE provider = 'keycloak' AND provider_user_id = ?)",
            USUARIO_DUP, SUB_GOOGLE, SUB_GOOGLE);
        // nunca provisionada por evento: não há listener assíncrono em corrida com esta limpeza
        jdbc.update("DELETE FROM auditoria WHERE usuario_id = ?", USUARIO_DUP);
    }

    private UUID usuarioDaDuplicata() {
        return jdbc.queryForObject(
            "SELECT usuario_id FROM usuario_identity_provider WHERE provider = 'keycloak' AND provider_user_id = ?",
            UUID.class, SUB_GOOGLE);
    }

    @Test
    @DisplayName("merge apaga a PESSOA da duplicata com o perfil do gate ainda pendente (FK customer_profile.usuario_id)")
    void testMergeDescartaPessoaComPerfilDoGate() throws Exception {
        // Achado do espelho (E7, login pelo Google sintético): o descarte da pessoa é JDBC
        // (DELETE FROM usuario) e rodava com o DELETE JPA do perfil ainda pendente →
        // violação de FK → 500. Por que testMergeCompleto não pegava: a suíte roda como
        // superuser e ENXERGA a trilha PESSOA_PROVISIONADA da duplicata (usuario_id) —
        // descartarPessoaSemPapeis cai no ramo tombstone (UPDATE, sem FK). Em produção o
        // backend é jetski_app, `auditoria` tem FORCE RLS e a linha global (tenant NULL) só
        // aparece com app.unrestricted: a contagem dá 0 e o ramo é o DELETE. Aqui a pessoa
        // é semeada sem trilha, para o teste passar pelo mesmo caminho que a produção.
        semearPessoaDaDuplicata();
        enviarElegivel();
        when(provisioning.transferFederatedIdentity(SUB_GOOGLE, SUB_OWNER, "google")).thenReturn(true);
        when(provisioning.deleteUser(SUB_GOOGLE)).thenReturn(true);

        UUID dupUsuario = usuarioDaDuplicata();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE usuario_id = ?",
            Integer.class, dupUsuario)).as("sem trilha: ramo DELETE, como em produção").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_profile WHERE usuario_id = ?",
            Integer.class, dupUsuario)).as("perfil do gate existe antes do merge").isEqualTo(1);

        mockMvc.perform(post("/v1/customers/self/cpf-merge/verificar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cpf\":\"" + CPF_OWNER + "\",\"codigo\":\"" + codigoNoRedis() + "\"}")
                .with(cliente(SUB_GOOGLE, "merge-dup@test.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mergeConcluido").value(true));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_profile WHERE usuario_id = ?",
            Integer.class, dupUsuario)).as("perfil da duplicata").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE id = ?",
            Integer.class, dupUsuario)).as("pessoa da duplicata (apagada, não tombstone)").isZero();
    }

    @Test
    @DisplayName("duplicata com papel de plataforma: merge recusado (400) sem tocar no provedor — antes apagava a conta Keycloak")
    void testMergeRecusaPessoaComPapeis() throws Exception {
        semearPessoaDaDuplicata();
        enviarElegivel();
        UUID dup = usuarioDaDuplicata();
        jdbc.update("INSERT INTO usuario_global_roles (usuario_id, roles, unrestricted_access) "
            + "VALUES (?, '{PLATFORM_LEITURA}', FALSE) ON CONFLICT DO NOTHING", dup);
        try {
            mockMvc.perform(post("/v1/customers/self/cpf-merge/verificar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cpf\":\"" + CPF_OWNER + "\",\"codigo\":\"" + codigoNoRedis() + "\"}")
                    .with(cliente(SUB_GOOGLE, "merge-dup@test.com")))
                .andExpect(status().isBadRequest());

            verify(provisioning, never()).transferFederatedIdentity(anyString(), anyString(), anyString());
            verify(provisioning, never()).deleteUser(anyString());
            // transação revertida: perfil e pessoa continuam como estavam
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_profile WHERE usuario_id = ?",
                Integer.class, dup)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE id = ? AND ativo",
                Integer.class, dup)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM usuario_global_roles WHERE usuario_id = ?", dup);
        }
    }

    @Test
    @DisplayName("duplicata referenciada por uma habilitação (FK): merge recusado (400), nada transferido — antes era 500 com o Google já transferido")
    void testMergeRecusaPessoaReferenciadaPorFk() throws Exception {
        semearPessoaDaDuplicata();
        enviarElegivel();
        UUID dup = usuarioDaDuplicata();
        jdbc.update("INSERT INTO customer_habilitacao (cpf, gru_numero, emitida_em, valida_ate, usuario_id) "
            + "VALUES ('99988877766', 'GRU-MERGE-FK-TESTE', now(), current_date + 365, ?) "
            + "ON CONFLICT (gru_numero) DO UPDATE SET usuario_id = EXCLUDED.usuario_id", dup);
        try {
            mockMvc.perform(post("/v1/customers/self/cpf-merge/verificar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cpf\":\"" + CPF_OWNER + "\",\"codigo\":\"" + codigoNoRedis() + "\"}")
                    .with(cliente(SUB_GOOGLE, "merge-dup@test.com")))
                .andExpect(status().isBadRequest());

            verify(provisioning, never()).transferFederatedIdentity(anyString(), anyString(), anyString());
            verify(provisioning, never()).deleteUser(anyString());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_profile WHERE usuario_id = ?",
                Integer.class, dup)).as("rollback devolveu o perfil").isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM customer_habilitacao WHERE gru_numero = 'GRU-MERGE-FK-TESTE'");
        }
    }

    @Test
    @DisplayName("reserva do portal com CPF de outra conta → 409 CPF_EM_USO")
    void testReservaComCpfDeOutraConta() throws Exception {
        // Seed na MARINA-BAY (não no ACME): jetski/modelo extras no ACME poluem
        // testes de agenda/fuel de outras classes (o ACME é o tenant mais
        // disputado da suíte). Mesmos IDs/valores do CustomerProfileIntegrationTest
        // (ON CONFLICT DO NOTHING — seed compartilhado e idempotente).
        UUID tenantMarina = UUID.fromString("b0000000-0000-0000-0000-000000000001");
        UUID modeloId = UUID.fromString("77777777-7777-4777-8777-000000000041");
        UUID jetskiId = UUID.fromString("77777777-7777-4777-8777-000000000042");
        jdbc.update("UPDATE tenant SET status = 'ATIVO', exibir_no_marketplace = true, " +
                    "pix_chave = 'pix@marina.com.br' WHERE id = ?", tenantMarina);
        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'Marina Modelo', 'Yamaha', 110, 2, 100.00, 5, 50.00, 300.00, FALSE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, modeloId, tenantMarina);
        jdbc.update("""
            INSERT INTO jetski (id, tenant_id, modelo_id, serie, ano, horimetro_atual, status, ativo)
            VALUES (?, ?, ?, 'JET-MARINA-1', 2024, 1.0, 'DISPONIVEL', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, jetskiId, tenantMarina, modeloId);

        java.time.LocalDateTime inicio = java.time.LocalDateTime.now()
            .plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);
        var iso = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        mockMvc.perform(post("/v1/customers/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"lojaSlug":"marina-bay","modeloId":"%s","dataInicio":"%s","dataFimPrevista":"%s",
                     "pagamentoTipo":"SINAL","cpf":"%s","telefone":"48999990000"}
                    """.formatted(modeloId, iso.format(inicio), iso.format(inicio.plusHours(1)), CPF_OWNER))
                .with(cliente(SUB_GOOGLE, "merge-dup@test.com")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.details.code").value("CPF_EM_USO"));
    }
}
