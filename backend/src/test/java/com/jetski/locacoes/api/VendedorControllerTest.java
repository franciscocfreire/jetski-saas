package com.jetski.locacoes.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.locacoes.api.dto.VendedorCreateRequest;
import com.jetski.locacoes.api.dto.VendedorUpdateRequest;
import com.jetski.locacoes.domain.Vendedor;
import com.jetski.locacoes.domain.VendedorTipo;
import com.jetski.locacoes.internal.repository.VendedorRepository;
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
 * Integration tests for VendedorController
 *
 * Tests CRUD operations for sellers/partners:
 * - List vendedores
 * - Get vendedor by ID
 * - Create vendedor
 * - Update vendedor
 * - Deactivate vendedor
 * - Reactivate vendedor
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@AutoConfigureMockMvc
@DisplayName("VendedorController Tests")
class VendedorControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VendedorRepository vendedorRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private Vendedor testVendedor;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);

        // Clean up dependent tables first (from V999 seed data) - in correct order
        jdbcTemplate.execute("DELETE FROM locacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
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
        vendedorRepository.deleteAll();

        // Mock OPA to allow all requests
        OPADecision allowDecision = OPADecision.builder()
                .allow(true)
                .tenantIsValid(true)
                .build();
        when(opaAuthorizationService.authorize(any(OPAInput.class)))
                .thenReturn(allowDecision);

        // Create test vendedor
        testVendedor = Vendedor.builder()
                .tenantId(TENANT_ID)
                .nome("João Silva")
                .documento("123.456.789-00")
                .tipo(VendedorTipo.PARCEIRO)
                .regraComissaoJson("{\"percentual_padrao\": 10.0}")
                .ativo(true)
                .build();
        testVendedor = vendedorRepository.save(testVendedor);
    }

    @AfterEach
    void tearDown() {
        vendedorRepository.deleteAll();
        TenantContext.clear();
    }

    // ========================================================================
    // Happy Path Tests
    // ========================================================================

    @Test
    @DisplayName("Should list active vendedores for tenant")
    void testListVendedores_ActiveOnly() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/vendedores", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(testVendedor.getId().toString()))
            .andExpect(jsonPath("$[0].nome").value("João Silva"))
            .andExpect(jsonPath("$[0].documento").value("123.456.789-00"))
            .andExpect(jsonPath("$[0].ativo").value(true));
    }

    @Test
    @DisplayName("Should get vendedor by ID")
    void testGetVendedor_ById() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/vendedores/{id}", TENANT_ID, testVendedor.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testVendedor.getId().toString()))
            .andExpect(jsonPath("$.nome").value("João Silva"))
            .andExpect(jsonPath("$.regraComissaoJson").exists());
    }

    @Test
    @DisplayName("Should create new vendedor")
    void testCreateVendedor() throws Exception {
        VendedorCreateRequest request = VendedorCreateRequest.builder()
                .nome("Maria Santos")
                .documento("987.654.321-00")
                .tipo(VendedorTipo.INTERNO)
                .regraComissaoJson("{\"percentual_padrao\": 12.0}")
                .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/vendedores", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.nome").value("Maria Santos"))
            .andExpect(jsonPath("$.documento").value("987.654.321-00"))
            .andExpect(jsonPath("$.regraComissaoJson").exists())
            .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("Should update existing vendedor")
    void testUpdateVendedor() throws Exception {
        VendedorUpdateRequest request = VendedorUpdateRequest.builder()
                .regraComissaoJson("{\"percentual_padrao\": 15.0}")
                .build();

        mockMvc.perform(put("/v1/tenants/{tenantId}/vendedores/{id}", TENANT_ID, testVendedor.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testVendedor.getId().toString()))
            .andExpect(jsonPath("$.regraComissaoJson").exists());
    }

    @Test
    @DisplayName("Should deactivate vendedor")
    void testDeactivateVendedor() throws Exception {
        mockMvc.perform(delete("/v1/tenants/{tenantId}/vendedores/{id}", TENANT_ID, testVendedor.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testVendedor.getId().toString()))
            .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    @DisplayName("Should reactivate inactive vendedor")
    void testReactivateVendedor() throws Exception {
        // Given: Deactivated vendedor
        testVendedor.setAtivo(false);
        vendedorRepository.save(testVendedor);

        mockMvc.perform(post("/v1/tenants/{tenantId}/vendedores/{id}/reactivate", TENANT_ID, testVendedor.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testVendedor.getId().toString()))
            .andExpect(jsonPath("$.ativo").value(true));
    }

    // ========================================================================
    // Error Cases
    // ========================================================================

    @Test
    @DisplayName("Should return 400 when vendedor not found")
    void testGetVendedor_NotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/v1/tenants/{tenantId}/vendedores/{id}", TENANT_ID, nonExistentId)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 401 for unauthenticated request")
    void testListVendedores_Unauthorized() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/vendedores", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 400 for invalid create request")
    void testCreateVendedor_InvalidRequest() throws Exception {
        VendedorCreateRequest request = VendedorCreateRequest.builder()
                .nome("")  // Invalid: empty
                .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/vendedores", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isBadRequest());
    }
}
