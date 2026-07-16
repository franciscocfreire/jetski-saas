package com.jetski.locacoes.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.locacoes.api.dto.ClienteCreateRequest;
import com.jetski.locacoes.api.dto.ClienteUpdateRequest;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ClienteController
 *
 * Tests CRUD operations for customers/renters:
 * - List clientes
 * - Get cliente by ID
 * - Create cliente
 * - Update cliente
 * - Accept liability terms (RF03.4)
 * - Deactivate cliente
 * - Reactivate cliente
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@AutoConfigureMockMvc
@DisplayName("ClienteController Tests")
class ClienteControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private Cliente testCliente;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);

        // Clean up dependent tables first (from V999 seed data) - in correct order
        // Dependentes primeiro (ordem de FK): sobras de OUTRAS classes (comissão,
        // fechamento, avaliação, fólio, docs) referenciam locacao/reserva do ACME e
        // quebravam esta limpeza quando a ordem das classes muda (CI 15-16/jul).
        jdbcTemplate.execute("DELETE FROM comissao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM avaliacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva_lancamento WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM locacao_item_opcional WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM foto WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM abastecimento WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM documento_emitido WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva_aceite WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva_comprovante WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva_habilitacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM locacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM locacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM politica_comissao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM fuel_policy WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");

        // Ensure test user has identity provider mapping (needed for authentication filter)
        try {
            jdbcTemplate.execute("INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id, linked_at) " +
                                 "VALUES ('11111111-1111-1111-1111-111111111111', 'keycloak', '11111111-1111-1111-1111-111111111111', NOW())");
        } catch (Exception ignored) {
            // Ignore if already exists
        }

        // Clean up any existing data first
        // tenant-scoped (deleteAll() cruzava tenants e esbarrava em FKs de
        // dados de outras classes — ex.: reservas do tenant E2E)
        jdbcTemplate.execute("DELETE FROM comissao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM avaliacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva_lancamento WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM locacao_item_opcional WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM foto WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM abastecimento WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM os_manutencao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM locacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM documento_emitido WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva_aceite WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva_comprovante WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva_habilitacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM cliente_anexo WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM cliente_claim_token WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM cliente_identity_provider WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM cliente WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");

        // Mock OPA to allow all requests
        OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();
        when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

        // Create test cliente
        testCliente = Cliente.builder()
                .tenantId(TENANT_ID)
                .nome("Maria Santos")
                .documento("987.654.321-00")
                .dataNascimento(java.time.LocalDate.of(1990, 5, 15))
                .genero("FEMININO")
                .email("maria.santos@email.com")
                .telefone("+5511912345678")
                .whatsapp(null)
                .enderecoJson("{\"cidade\": \"São Paulo\", \"estado\": \"SP\"}")
                .termoAceite(false)
                .ativo(true)
                .build();
        testCliente = clienteRepository.save(testCliente);
    }

    @AfterEach
    void tearDown() {
        // tenant-scoped (deleteAll() cruzava tenants e esbarrava em FKs de
        // dados de outras classes — ex.: reservas do tenant E2E)
        jdbcTemplate.execute("DELETE FROM comissao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM avaliacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva_lancamento WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM locacao_item_opcional WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM foto WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM abastecimento WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM os_manutencao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM locacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM documento_emitido WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva_aceite WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva_comprovante WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva_habilitacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM cliente_anexo WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM cliente_claim_token WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM cliente_identity_provider WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM cliente WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        TenantContext.clear();
    }

    // ========================================================================
    // Happy Path Tests
    // ========================================================================

    @Test
    @DisplayName("Should list active clientes for tenant")
    void testListClientes_ActiveOnly() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/clientes", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            // filtra pelo item semeado (linhas de outros tenants podem aparecer
            // no runner de testes — RLS bypass como superuser)
            .andExpect(jsonPath("$[?(@.id == '" + testCliente.getId() + "')].nome").value("Maria Santos"))
            .andExpect(jsonPath("$[?(@.id == '" + testCliente.getId() + "')].documento").value("987.654.321-00"))
            .andExpect(jsonPath("$[?(@.id == '" + testCliente.getId() + "')].ativo").value(true));
    }

    @Test
    @DisplayName("Should get cliente by ID")
    void testGetCliente_ById() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/clientes/{id}", TENANT_ID, testCliente.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testCliente.getId().toString()))
            .andExpect(jsonPath("$.nome").value("Maria Santos"))
            .andExpect(jsonPath("$.termoAceite").value(false));
    }

    @Test
    @DisplayName("Should create new cliente")
    void testCreateCliente() throws Exception {
        ClienteCreateRequest request = ClienteCreateRequest.builder()
                .nome("João Silva")
                .documento("123.456.789-00")
                .dataNascimento(java.time.LocalDate.of(1985, 3, 20))
                .genero("MASCULINO")
                .email("joao.silva@email.com")
                .telefone("+5511987654321")
                .whatsapp("+5511987654321")
                .enderecoJson("{\"cidade\": \"Rio de Janeiro\", \"estado\": \"RJ\"}")
                .termoAceite(false)
                .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/clientes", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.nome").value("João Silva"))
            .andExpect(jsonPath("$.documento").value("123.456.789-00"))
            .andExpect(jsonPath("$.email").value("joao.silva@email.com"))
            .andExpect(jsonPath("$.telefone").value("+5511987654321"))
            .andExpect(jsonPath("$.termoAceite").value(false))
            .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("Should create pré-conta (BALCAO/PRE_CONTA) - F2.2")
    void testCriarPreConta() throws Exception {
        ClienteCreateRequest request = ClienteCreateRequest.builder()
                .nome("Roberto Lima")
                .documento("321.654.987-00")
                .email("roberto.lima@email.com")
                .telefone("+5521988881234")
                .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/clientes/pre-conta", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.origem").value("BALCAO"))
            .andExpect(jsonPath("$.statusConta").value("PRE_CONTA"))
            .andExpect(jsonPath("$.nome").value("Roberto Lima"));
    }

    @Test
    @DisplayName("Should find cliente by CPF (dedupe) - F2.2")
    void testBuscarPorCpf() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/clientes", TENANT_ID)
                .param("cpf", "987.654.321-00")
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].documento").value("987.654.321-00"));
    }

    @Test
    @DisplayName("Should dedupe pré-conta when CPF already exists (not active) - F2.2")
    void testCriarPreConta_DedupeReusaExistente() throws Exception {
        ClienteCreateRequest request = ClienteCreateRequest.builder()
                .nome("Maria Santos (balcão)")
                .documento("987.654.321-00") // mesmo do testCliente (SEM_LOGIN)
                .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/clientes/pre-conta", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(testCliente.getId().toString()));
    }

    @Test
    @DisplayName("Should block pré-conta when CPF has ACTIVE account (anti-takeover) - F2.2")
    void testCriarPreConta_AntiTakeover() throws Exception {
        Cliente ativo = clienteRepository.save(Cliente.builder()
                .tenantId(TENANT_ID)
                .nome("Cliente Ativo")
                .documento("555.444.333-22")
                .statusConta(Cliente.StatusConta.ATIVA)
                .ativo(true)
                .build());

        ClienteCreateRequest request = ClienteCreateRequest.builder()
                .nome("Tentativa Balcão")
                .documento("555.444.333-22")
                .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/clientes/pre-conta", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should update existing cliente")
    void testUpdateCliente() throws Exception {
        ClienteUpdateRequest request = ClienteUpdateRequest.builder()
                .email("maria.santos.nova@email.com")
                .telefone("+5511912349999")
                .whatsapp("+5511912349999")
                .build();

        mockMvc.perform(put("/v1/tenants/{tenantId}/clientes/{id}", TENANT_ID, testCliente.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testCliente.getId().toString()))
            .andExpect(jsonPath("$.email").value("maria.santos.nova@email.com"))
            .andExpect(jsonPath("$.telefone").value("+5511912349999"));
    }

    @Test
    @DisplayName("Should accept liability terms (RF03.4)")
    void testAcceptTerms() throws Exception {
        mockMvc.perform(post("/v1/tenants/{tenantId}/clientes/{id}/accept-terms", TENANT_ID, testCliente.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testCliente.getId().toString()))
            .andExpect(jsonPath("$.termoAceite").value(true));
    }

    @Test
    @DisplayName("Should deactivate cliente")
    void testDeactivateCliente() throws Exception {
        mockMvc.perform(delete("/v1/tenants/{tenantId}/clientes/{id}", TENANT_ID, testCliente.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testCliente.getId().toString()))
            .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    @DisplayName("Should reactivate inactive cliente")
    void testReactivateCliente() throws Exception {
        // Given: Deactivated cliente
        testCliente.setAtivo(false);
        clienteRepository.save(testCliente);

        mockMvc.perform(post("/v1/tenants/{tenantId}/clientes/{id}/reactivate", TENANT_ID, testCliente.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testCliente.getId().toString()))
            .andExpect(jsonPath("$.ativo").value(true));
    }

    // ========================================================================
    // Error Cases
    // ========================================================================

    @Test
    @DisplayName("Should return 400 when cliente not found")
    void testGetCliente_NotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/v1/tenants/{tenantId}/clientes/{id}", TENANT_ID, nonExistentId)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 401 for unauthenticated request")
    void testListClientes_Unauthorized() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/clientes", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 400 for invalid create request")
    void testCreateCliente_InvalidRequest() throws Exception {
        ClienteCreateRequest request = ClienteCreateRequest.builder()
                .nome("")  // Invalid: empty
                .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/clientes", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 200 when trying to accept terms for already accepted cliente (idempotent)")
    void testAcceptTerms_AlreadyAccepted() throws Exception {
        // Given: Cliente who already accepted terms
        testCliente.setTermoAceite(true);
        clienteRepository.save(testCliente);

        mockMvc.perform(post("/v1/tenants/{tenantId}/clientes/{id}/accept-terms", TENANT_ID, testCliente.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.termoAceite").value(true));
    }
}
