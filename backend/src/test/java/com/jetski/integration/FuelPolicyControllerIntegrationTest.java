package com.jetski.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.combustivel.api.dto.FuelPolicyCreateRequest;
import com.jetski.combustivel.domain.FuelChargeMode;
import com.jetski.combustivel.domain.FuelPolicyType;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.security.TenantAccessInfo;
import com.jetski.usuarios.internal.TenantAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentMatchers;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for FuelPolicyController
 *
 * Tests fuel policy management API endpoints with database and Spring context
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@AutoConfigureMockMvc
@DisplayName("Integration: FuelPolicyController")
class FuelPolicyControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    @MockBean
    private TenantAccessService tenantAccessService;

    private static final UUID TENANT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID JETSKI_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MODELO_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        // Mock tenant access
        TenantAccessInfo allowedAccess = TenantAccessInfo.builder()
            .hasAccess(true)
            .roles(List.of("GERENTE", "ADMIN_TENANT"))
            .unrestricted(false)
            .usuarioId(USER_ID) // CRITICAL: Set usuarioId for TenantContext.getUsuarioId()
            .build();

        when(tenantAccessService.validateAccess(any(String.class), eq(USER_ID.toString()), any(UUID.class)))
            .thenReturn(allowedAccess);

        // Mock OPA to allow all requests
        OPADecision allowDecision = OPADecision.builder()
            .allow(true)
            .tenantIsValid(true)
            .build();

        when(opaAuthorizationService.authorize(any(OPAInput.class)))
            .thenReturn(allowDecision);
    }

    // ===================================================================
    // POST /v1/tenants/{tenantId}/fuel-policies - Create policy
    // ===================================================================

    @Test
    @DisplayName("POST /fuel-policies - Should create GLOBAL INCLUSO policy")
    void testCriarPolitica_GlobalIncluso() throws Exception {
        FuelPolicyCreateRequest request = FuelPolicyCreateRequest.builder()
            .nome("Combustível Incluso Global")
            .tipo(FuelChargeMode.INCLUSO)
            .aplicavelA(FuelPolicyType.GLOBAL)
            .referenciaId(null) // GLOBAL doesn't need referenciaId
            .valorTaxaPorHora(null)
            .comissionavel(false)
            .ativo(true)
            .prioridade(10)
            .descricao("Política padrão: combustível incluído no preço")
            .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/fuel-policies", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
            .andExpect(jsonPath("$.nome").value("Combustível Incluso Global"))
            .andExpect(jsonPath("$.tipo").value("INCLUSO"))
            .andExpect(jsonPath("$.aplicavelA").value("GLOBAL"))
            .andExpect(jsonPath("$.referenciaId").doesNotExist())
            .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("POST /fuel-policies - Should create MODELO TAXA_FIXA policy")
    void testCriarPolitica_ModeloTaxaFixa() throws Exception {
        FuelPolicyCreateRequest request = FuelPolicyCreateRequest.builder()
            .nome("Taxa Fixa Modelo SeaDoo")
            .tipo(FuelChargeMode.TAXA_FIXA)
            .aplicavelA(FuelPolicyType.MODELO)
            .referenciaId(MODELO_ID)
            .valorTaxaPorHora(new BigDecimal("15.00"))
            .comissionavel(false)
            .ativo(true)
            .prioridade(20)
            .descricao("R$ 15/hora para modelo SeaDoo")
            .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/fuel-policies", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tipo").value("TAXA_FIXA"))
            .andExpect(jsonPath("$.aplicavelA").value("MODELO"))
            .andExpect(jsonPath("$.referenciaId").value(MODELO_ID.toString()))
            .andExpect(jsonPath("$.valorTaxaPorHora").value(15.00));
    }

    @Test
    @DisplayName("POST /fuel-policies - Should create JETSKI MEDIDO policy")
    void testCriarPolitica_JetskiMedido() throws Exception {
        FuelPolicyCreateRequest request = FuelPolicyCreateRequest.builder()
            .nome("Medido Jetski 123")
            .tipo(FuelChargeMode.MEDIDO)
            .aplicavelA(FuelPolicyType.JETSKI)
            .referenciaId(JETSKI_ID)
            .valorTaxaPorHora(null) // MEDIDO doesn't use valorTaxaPorHora
            .comissionavel(false)
            .ativo(true)
            .prioridade(30)
            .descricao("Cobrança por litros consumidos")
            .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/fuel-policies", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tipo").value("MEDIDO"))
            .andExpect(jsonPath("$.aplicavelA").value("JETSKI"))
            .andExpect(jsonPath("$.referenciaId").value(JETSKI_ID.toString()));
    }

    @Test
    @DisplayName("POST /fuel-policies - Should return 400 for missing required fields")
    void testCriarPolitica_ValidationError() throws Exception {
        FuelPolicyCreateRequest request = FuelPolicyCreateRequest.builder()
            .nome(null) // Missing required field
            .tipo(FuelChargeMode.TAXA_FIXA)
            .aplicavelA(FuelPolicyType.GLOBAL)
            .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/fuel-policies", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    // ===================================================================
    // GET /v1/tenants/{tenantId}/fuel-policies - List policies
    // ===================================================================

    @Test
    @DisplayName("GET /fuel-policies - Should list all policies")
    void testListarPoliticas() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/fuel-policies", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /fuel-policies - Should filter by aplicavelA")
    void testListarPoliticas_FilterByAplicavelA() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/fuel-policies", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .param("aplicavelA", "GLOBAL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /fuel-policies - Should filter by ativo")
    void testListarPoliticas_FilterByAtivo() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/fuel-policies", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .param("ativo", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ===================================================================
    // GET /v1/tenants/{tenantId}/fuel-policies/{id} - Get by ID
    // ===================================================================

    @Test
    @DisplayName("GET /fuel-policies/{id} - Should return 404 for non-existent policy")
    void testBuscarPolitica_NotFound() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/fuel-policies/{id}", TENANT_ID, 99999L)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isNotFound());
    }

    // ===================================================================
    // GET /v1/tenants/{tenantId}/fuel-policies/applicable - Get applicable policy
    // ===================================================================

    @Test
    @DisplayName("GET /fuel-policies/applicable - Should return applicable policy")
    void testBuscarPoliticaAplicavel_Success() throws Exception {
        // This test validates that the hierarchical lookup works
        // It may return GLOBAL policy from seed data or any created policy
        mockMvc.perform(get("/v1/tenants/{tenantId}/fuel-policies/applicable", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .param("jetskiId", JETSKI_ID.toString())
                .param("modeloId", MODELO_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.tipo").exists());
    }

    // ===================================================================
    // Security tests
    // ===================================================================

    @Test
    @DisplayName("Should return 401 when no authentication")
    void testCriarPolitica_NoAuth() throws Exception {
        FuelPolicyCreateRequest request = FuelPolicyCreateRequest.builder()
            .nome("Test Policy")
            .tipo(FuelChargeMode.INCLUSO)
            .aplicavelA(FuelPolicyType.GLOBAL)
            .ativo(true)
            .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/fuel-policies", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 400 when tenant mismatch")
    void testCriarPolitica_TenantMismatch() throws Exception {
        UUID differentTenantId = UUID.randomUUID();

        FuelPolicyCreateRequest request = FuelPolicyCreateRequest.builder()
            .nome("Test Policy")
            .tipo(FuelChargeMode.INCLUSO)
            .aplicavelA(FuelPolicyType.GLOBAL)
            .ativo(true)
            .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/fuel-policies", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", differentTenantId.toString()) // Different tenant!
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
}
