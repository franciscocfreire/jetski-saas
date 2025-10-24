package com.jetski.locacoes.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.locacoes.api.dto.JetskiCreateRequest;
import com.jetski.locacoes.api.dto.JetskiUpdateRequest;
import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.JetskiStatus;
import com.jetski.locacoes.domain.Modelo;
import com.jetski.locacoes.internal.repository.JetskiRepository;
import com.jetski.locacoes.internal.repository.ModeloRepository;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for JetskiController
 *
 * Tests CRUD operations for jetski fleet:
 * - List jetskis
 * - Get jetski by ID
 * - Create jetski
 * - Update jetski
 * - Update jetski status
 * - Deactivate jetski
 * - Reactivate jetski
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@AutoConfigureMockMvc
@DisplayName("JetskiController Tests")
class JetskiControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JetskiRepository jetskiRepository;

    @Autowired
    private ModeloRepository modeloRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private Modelo testModelo;
    private Jetski testJetski;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);

        // Clean up dependent tables first (from V999 seed data) - in correct order
        jdbcTemplate.execute("DELETE FROM locacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM os_manutencao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM commission_policy WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM fuel_policy WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");

        // Ensure test user has identity provider mapping (needed for authentication filter)
        try {
            jdbcTemplate.execute("INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id, linked_at) " +
                                 "VALUES ('11111111-1111-1111-1111-111111111111', 'keycloak', '11111111-1111-1111-1111-111111111111', NOW())");
        } catch (Exception ignored) {
            // Ignore if already exists
        }

        // Clean up any existing data first
        jetskiRepository.deleteAll();
        modeloRepository.deleteAll();

        // Mock OPA to allow all requests
        OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();
        when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

        // Create test modelo
        testModelo = Modelo.builder()
                .tenantId(TENANT_ID)
                .fabricante("Sea-Doo")
                .nome("GTI SE 170")
                .precoBaseHora(new BigDecimal("200.00"))
                .ativo(true)
                .build();
        testModelo = modeloRepository.save(testModelo);

        // Create test jetski
        testJetski = Jetski.builder()
                .tenantId(TENANT_ID)
                .modeloId(testModelo.getId())
                .serie("SDI-GTI-2024-001")
                .horimetroAtual(BigDecimal.ZERO)
                .status(JetskiStatus.DISPONIVEL)
                .ativo(true)
                .build();
        testJetski = jetskiRepository.save(testJetski);
    }

    @AfterEach
    void tearDown() {
        jetskiRepository.deleteAll();
        modeloRepository.deleteAll();
        TenantContext.clear();
    }

    // ========================================================================
    // Happy Path Tests
    // ========================================================================

    @Test
    @DisplayName("Should list active jetskis for tenant")
    void testListJetskis_ActiveOnly() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/jetskis", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(testJetski.getId().toString()))
            .andExpect(jsonPath("$[0].serie").value("SDI-GTI-2024-001"))
            .andExpect(jsonPath("$[0].status").value("DISPONIVEL"))
            .andExpect(jsonPath("$[0].ativo").value(true));
    }

    @Test
    @DisplayName("Should get jetski by ID")
    void testGetJetski_ById() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/jetskis/{id}", TENANT_ID, testJetski.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testJetski.getId().toString()))
            .andExpect(jsonPath("$.serie").value("SDI-GTI-2024-001"))
            .andExpect(jsonPath("$.modeloId").value(testModelo.getId().toString()));
    }

    @Test
    @DisplayName("Should create new jetski")
    void testCreateJetski() throws Exception {
        JetskiCreateRequest request = JetskiCreateRequest.builder()
                .modeloId(testModelo.getId())
                .serie("SDI-GTI-2024-002")
                .horimetroAtual(BigDecimal.ZERO)
                .status(JetskiStatus.DISPONIVEL)
                .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/jetskis", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.serie").value("SDI-GTI-2024-002"))
            .andExpect(jsonPath("$.status").value("DISPONIVEL"))
            .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("Should update existing jetski")
    void testUpdateJetski() throws Exception {
        JetskiUpdateRequest request = JetskiUpdateRequest.builder()
                .horimetroAtual(new BigDecimal("150"))
                .build();

        mockMvc.perform(put("/v1/tenants/{tenantId}/jetskis/{id}", TENANT_ID, testJetski.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testJetski.getId().toString()))
            .andExpect(jsonPath("$.horimetroAtual").value(150));
    }

    @Test
    @DisplayName("Should update jetski status")
    void testUpdateJetskiStatus() throws Exception {
        mockMvc.perform(patch("/v1/tenants/{tenantId}/jetskis/{id}/status", TENANT_ID, testJetski.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .param("status", "MANUTENCAO")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testJetski.getId().toString()))
            .andExpect(jsonPath("$.status").value("MANUTENCAO"));
    }

    @Test
    @DisplayName("Should deactivate jetski")
    void testDeactivateJetski() throws Exception {
        mockMvc.perform(delete("/v1/tenants/{tenantId}/jetskis/{id}", TENANT_ID, testJetski.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testJetski.getId().toString()))
            .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    @DisplayName("Should reactivate inactive jetski")
    void testReactivateJetski() throws Exception {
        // Given: Deactivated jetski
        testJetski.setAtivo(false);
        jetskiRepository.save(testJetski);

        mockMvc.perform(post("/v1/tenants/{tenantId}/jetskis/{id}/reactivate", TENANT_ID, testJetski.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testJetski.getId().toString()))
            .andExpect(jsonPath("$.ativo").value(true));
    }

    // ========================================================================
    // Error Cases
    // ========================================================================

    @Test
    @DisplayName("Should return 400 when jetski not found")
    void testGetJetski_NotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/v1/tenants/{tenantId}/jetskis/{id}", TENANT_ID, nonExistentId)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 401 for unauthenticated request")
    void testListJetskis_Unauthorized() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/jetskis", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 400 for invalid create request")
    void testCreateJetski_InvalidRequest() throws Exception {
        JetskiCreateRequest request = JetskiCreateRequest.builder()
                .serie("")  // Invalid: empty
                .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/jetskis", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isBadRequest());
    }
}
