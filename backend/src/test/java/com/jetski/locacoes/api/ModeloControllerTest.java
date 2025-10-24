package com.jetski.locacoes.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.locacoes.api.dto.ModeloCreateRequest;
import com.jetski.locacoes.api.dto.ModeloUpdateRequest;
import com.jetski.locacoes.domain.Modelo;
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
 * Integration tests for ModeloController
 *
 * Tests CRUD operations for jetski models:
 * - List modelos (active and with inactive)
 * - Get modelo by ID
 * - Create modelo
 * - Update modelo
 * - Deactivate modelo
 * - Reactivate modelo
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@AutoConfigureMockMvc
@DisplayName("ModeloController Tests")
class ModeloControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    @BeforeEach
    void setUp() {
        // Set tenant context
        TenantContext.setTenantId(TENANT_ID);

        // Clean up dependent tables first (from V999 seed data) - in correct order
        jdbcTemplate.execute("DELETE FROM locacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM os_manutencao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM jetski WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
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
    }

    @AfterEach
    void tearDown() {
        modeloRepository.deleteAll();
        TenantContext.clear();
    }

    // ========================================================================
    // Happy Path Tests
    // ========================================================================

    @Test
    @DisplayName("Should list active modelos for tenant")
    void testListModelos_ActiveOnly() throws Exception {
        // Given: One active modelo exists

        // When/Then: GET /v1/tenants/{tenantId}/modelos
        mockMvc.perform(get("/v1/tenants/{tenantId}/modelos", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(testModelo.getId().toString()))
            .andExpect(jsonPath("$[0].fabricante").value("Sea-Doo"))
            .andExpect(jsonPath("$[0].nome").value("GTI SE 170"))
            .andExpect(jsonPath("$[0].precoBaseHora").value(200.00))
            .andExpect(jsonPath("$[0].ativo").value(true));
    }

    @Test
    @DisplayName("Should list all modelos including inactive when flag is true")
    void testListModelos_IncludeInactive() throws Exception {
        // Given: Create inactive modelo
        Modelo inactiveModelo = Modelo.builder()
                .tenantId(TENANT_ID)
                .fabricante("Yamaha")
                .nome("VX Cruiser HO")
                .precoBaseHora(new BigDecimal("180.00"))
                .ativo(false)
                .build();
        modeloRepository.save(inactiveModelo);

        // When/Then: GET /v1/tenants/{tenantId}/modelos?includeInactive=true
        mockMvc.perform(get("/v1/tenants/{tenantId}/modelos", TENANT_ID)
                .param("includeInactive", "true")
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("Should get modelo by ID")
    void testGetModelo_ById() throws Exception {
        // When/Then: GET /v1/tenants/{tenantId}/modelos/{id}
        mockMvc.perform(get("/v1/tenants/{tenantId}/modelos/{id}", TENANT_ID, testModelo.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testModelo.getId().toString()))
            .andExpect(jsonPath("$.fabricante").value("Sea-Doo"))
            .andExpect(jsonPath("$.nome").value("GTI SE 170"));
    }

    @Test
    @DisplayName("Should create new modelo")
    void testCreateModelo() throws Exception {
        // Given: Create request
        ModeloCreateRequest request = ModeloCreateRequest.builder()
                .fabricante("Kawasaki")
                .nome("Ultra 310LX")
                .precoBaseHora(new BigDecimal("250.00"))
                .build();

        // When/Then: POST /v1/tenants/{tenantId}/modelos
        mockMvc.perform(post("/v1/tenants/{tenantId}/modelos", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.fabricante").value("Kawasaki"))
            .andExpect(jsonPath("$.nome").value("Ultra 310LX"))
            .andExpect(jsonPath("$.precoBaseHora").value(250.00))
            .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("Should update existing modelo")
    void testUpdateModelo() throws Exception {
        // Given: Update request
        ModeloUpdateRequest request = ModeloUpdateRequest.builder()
                .precoBaseHora(new BigDecimal("220.00"))
                .build();

        // When/Then: PUT /v1/tenants/{tenantId}/modelos/{id}
        mockMvc.perform(put("/v1/tenants/{tenantId}/modelos/{id}", TENANT_ID, testModelo.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testModelo.getId().toString()))
            .andExpect(jsonPath("$.precoBaseHora").value(220.00));
    }

    @Test
    @DisplayName("Should deactivate modelo")
    void testDeactivateModelo() throws Exception {
        // When/Then: DELETE /v1/tenants/{tenantId}/modelos/{id}
        mockMvc.perform(delete("/v1/tenants/{tenantId}/modelos/{id}", TENANT_ID, testModelo.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testModelo.getId().toString()))
            .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    @DisplayName("Should reactivate inactive modelo")
    void testReactivateModelo() throws Exception {
        // Given: Deactivated modelo
        testModelo.setAtivo(false);
        modeloRepository.save(testModelo);

        // When/Then: POST /v1/tenants/{tenantId}/modelos/{id}/reactivate
        mockMvc.perform(post("/v1/tenants/{tenantId}/modelos/{id}/reactivate", TENANT_ID, testModelo.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testModelo.getId().toString()))
            .andExpect(jsonPath("$.ativo").value(true));
    }

    // ========================================================================
    // Error Cases
    // ========================================================================

    @Test
    @DisplayName("Should return 400 when modelo not found")
    void testGetModelo_NotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        // When/Then: GET /v1/tenants/{tenantId}/modelos/{id}
        mockMvc.perform(get("/v1/tenants/{tenantId}/modelos/{id}", TENANT_ID, nonExistentId)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 401 for unauthenticated request")
    void testListModelos_Unauthorized() throws Exception {
        // When/Then: GET /v1/tenants/{tenantId}/modelos without JWT
        mockMvc.perform(get("/v1/tenants/{tenantId}/modelos", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 400 for invalid create request")
    void testCreateModelo_InvalidRequest() throws Exception {
        // Given: Invalid request (missing required fields)
        ModeloCreateRequest request = ModeloCreateRequest.builder()
                .fabricante("")  // Invalid: empty
                .build();

        // When/Then: POST /v1/tenants/{tenantId}/modelos
        mockMvc.perform(post("/v1/tenants/{tenantId}/modelos", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isBadRequest());
    }
}
