package com.jetski.locacoes;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.security.DuplicateUserException;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0 do Portal do Cliente: auto-cadastro público (identidade global, role
 * CLIENTE) e escopo /v1/customers/** (self + vínculos multi-loja, sem tenant
 * no token).
 */
@AutoConfigureMockMvc
@DisplayName("Portal do Cliente — P0 (identidade)")
class CustomerPortalIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TransactionTemplate transactionTemplate;

    @MockBean OPAAuthorizationService opaAuthorizationService;
    @MockBean UserProvisioningService userProvisioningService;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID TENANT_MARINA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final String CUSTOMER_SUB = "cccccccc-0000-0000-0000-000000000001";

    @BeforeEach
    void setUp() {
        when(opaAuthorizationService.authorize(org.mockito.ArgumentMatchers.any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());

        // Limpa vínculos/clientes deste teste (por sub fixo) nos dois tenants
        transactionTemplate.executeWithoutResult(tx -> {
            for (UUID tenant : new UUID[]{TENANT_ACME, TENANT_MARINA}) {
                jdbcTemplate.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenant.toString());
                jdbcTemplate.update("DELETE FROM cliente_identity_provider WHERE provider_user_id = ?", CUSTOMER_SUB);
                jdbcTemplate.update("DELETE FROM cliente WHERE email = 'portal.p0@test.com'");
            }
        });
    }

    private RequestPostProcessor cliente() {
        return jwt().jwt(j -> j.subject(CUSTOMER_SUB)
                .claim("name", "Cliente Portal")
                .claim("email", "portal.p0@test.com")
                .claim("email_verified", true))
            .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private void seedVinculo(UUID tenant, String nomeCliente) {
        transactionTemplate.executeWithoutResult(tx -> {
            jdbcTemplate.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenant.toString());
            UUID clienteId = UUID.randomUUID();
            jdbcTemplate.update(
                "INSERT INTO cliente (id, tenant_id, nome, email, origem, status_conta) " +
                "VALUES (?, ?, ?, 'portal.p0@test.com', 'PORTAL', 'ATIVA')",
                clienteId, tenant, nomeCliente);
            jdbcTemplate.update(
                "INSERT INTO cliente_identity_provider (tenant_id, cliente_id, provider, provider_user_id) " +
                "VALUES (?, ?, 'keycloak', ?)",
                tenant, clienteId, CUSTOMER_SUB);
        });
    }

    // ============================== Signup público ==============================

    @Test
    @DisplayName("Signup público provisiona identidade global (role CLIENTE)")
    void testSignup() throws Exception {
        when(userProvisioningService.provisionCustomer(anyString(), anyString(), anyString()))
            .thenReturn("kc-sub-novo");

        mockMvc.perform(post("/v1/public/customers/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"João da Silva\",\"email\":\"Joao@Test.com\",\"senha\":\"senhaForte1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").exists());

        // e-mail normalizado (lowercase) antes do provisioning
        verify(userProvisioningService).provisionCustomer("joao@test.com", "João da Silva", "senhaForte1");
    }

    @Test
    @DisplayName("Signup rejeita e-mail duplicado com mensagem de negócio")
    void testSignupDuplicado() throws Exception {
        when(userProvisioningService.provisionCustomer(anyString(), anyString(), anyString()))
            .thenThrow(new DuplicateUserException("dup@test.com"));

        mockMvc.perform(post("/v1/public/customers/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"João da Silva\",\"email\":\"dup@test.com\",\"senha\":\"senhaForte1\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("Já existe uma conta")));
    }

    @Test
    @DisplayName("Signup valida payload (senha curta = 400)")
    void testSignupValidacao() throws Exception {
        mockMvc.perform(post("/v1/public/customers/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"João\",\"email\":\"x@test.com\",\"senha\":\"123\"}"))
            .andExpect(status().isBadRequest());
    }

    // ============================== /v1/customers/self ==============================

    @Test
    @DisplayName("Self retorna perfil + vínculos de TODAS as lojas (cross-tenant, sem header)")
    void testSelfComVinculos() throws Exception {
        seedVinculo(TENANT_ACME, "Cliente Acme");
        seedVinculo(TENANT_MARINA, "Cliente Marina");

        mockMvc.perform(get("/v1/customers/self").with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Cliente Portal"))
            .andExpect(jsonPath("$.email").value("portal.p0@test.com"))
            .andExpect(jsonPath("$.emailVerified").value(true))
            .andExpect(jsonPath("$.lojas.length()").value(2));
    }

    @Test
    @DisplayName("Self sem vínculos retorna lista vazia (conta global recém-criada)")
    void testSelfSemVinculos() throws Exception {
        mockMvc.perform(get("/v1/customers/self").with(cliente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lojas.length()").value(0));
    }

    @Test
    @DisplayName("Staff TAMBÉM é cliente da plataforma: acessa o escopo customer com a persona CLIENTE")
    void testStaffTambemECliente() throws Exception {
        // staff de um tenant pode alugar jetski como cliente — o isolamento é
        // por sub/vínculos; sem vínculos, vê o próprio perfil vazio
        mockMvc.perform(get("/v1/customers/self")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                        .claim("name", "Staff Cliente")
                        .claim("email", "staff.cliente@test.com"))
                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lojas.length()").value(0));

        // anônimo continua barrado
        mockMvc.perform(get("/v1/customers/self"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Sem autenticação — 401")
    void testAnonimoNaoAcessa() throws Exception {
        mockMvc.perform(get("/v1/customers/self"))
            .andExpect(status().isUnauthorized());
    }
}
