package com.jetski.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.combustivel.api.dto.FuelPriceDayCreateRequest;
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
import java.time.LocalDate;
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
 * Integration tests for FuelPriceDayController
 *
 * Tests daily fuel price management API endpoints with database and Spring context
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@AutoConfigureMockMvc
@DisplayName("Integration: FuelPriceDayController")
class FuelPriceDayControllerIntegrationTest extends AbstractIntegrationTest {

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

    @BeforeEach
    void setUp() {
        // Mock tenant access with conditional logic for tenant matching
        when(tenantAccessService.validateAccess(any(String.class), eq(USER_ID.toString()), any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID requestedTenantId = invocation.getArgument(2, UUID.class);
                if (requestedTenantId.equals(TENANT_ID)) {
                    return TenantAccessInfo.builder()
                        .hasAccess(true)
                        .roles(List.of("GERENTE", "ADMIN_TENANT"))
                        .unrestricted(false)
                        .usuarioId(USER_ID) // CRITICAL: Set usuarioId for TenantContext.getUsuarioId()
                        .build();
                } else {
                    return TenantAccessInfo.builder()
                        .hasAccess(false)
                        .roles(List.of())
                        .unrestricted(false)
                        .build();
                }
            });

        // Mock OPA to allow all requests
        OPADecision allowDecision = OPADecision.builder()
            .allow(true)
            .tenantIsValid(true)
            .build();

        when(opaAuthorizationService.authorize(any(OPAInput.class)))
            .thenReturn(allowDecision);
    }

    // ===================================================================
    // POST /v1/tenants/{tenantId}/fuel-prices - Create/override price
    // ===================================================================

    @Test
    @DisplayName("POST /fuel-prices - Should create daily price (admin override)")
    void testCriarPreco() throws Exception {
        LocalDate data = LocalDate.now();

        FuelPriceDayCreateRequest request = FuelPriceDayCreateRequest.builder()
            .data(data)
            .precoMedioLitro(new BigDecimal("7.50"))
            .totalLitrosAbastecidos(new BigDecimal("100.00"))
            .totalCusto(new BigDecimal("750.00"))
            .qtdAbastecimentos(2)
            .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/fuel-prices", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
            .andExpect(jsonPath("$.data").value(data.toString()))
            .andExpect(jsonPath("$.precoMedioLitro").value(7.50))
            .andExpect(jsonPath("$.totalLitrosAbastecidos").value(100.00))
            .andExpect(jsonPath("$.totalCusto").value(750.00))
            .andExpect(jsonPath("$.qtdAbastecimentos").value(2));
    }

    @Test
    @DisplayName("POST /fuel-prices - Should return 400 for missing required fields")
    void testCriarPreco_ValidationError() throws Exception {
        FuelPriceDayCreateRequest request = FuelPriceDayCreateRequest.builder()
            .data(null) // Missing required field
            .precoMedioLitro(new BigDecimal("7.50"))
            .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/fuel-prices", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    // ===================================================================
    // GET /v1/tenants/{tenantId}/fuel-prices - List prices
    // ===================================================================

    @Test
    @DisplayName("GET /fuel-prices - Should list all prices")
    void testListarPrecos() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/fuel-prices", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /fuel-prices - Should filter by date range")
    void testListarPrecos_FilterByDateRange() throws Exception {
        LocalDate dataInicio = LocalDate.now().minusDays(7);
        LocalDate dataFim = LocalDate.now();

        mockMvc.perform(get("/v1/tenants/{tenantId}/fuel-prices", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .param("dataInicio", dataInicio.toString())
                .param("dataFim", dataFim.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ===================================================================
    // GET /v1/tenants/{tenantId}/fuel-prices/{data} - Get by date
    // ===================================================================

    @Test
    @DisplayName("GET /fuel-prices/{data} - Should return 404 for non-existent date")
    void testBuscarPrecoPorData_NotFound() throws Exception {
        LocalDate data = LocalDate.now().minusYears(10); // Very old date

        mockMvc.perform(get("/v1/tenants/{tenantId}/fuel-prices/{data}", TENANT_ID, data)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isNotFound());
    }

    // ===================================================================
    // GET /v1/tenants/{tenantId}/fuel-prices/average - Get average price
    // ===================================================================

    @Test
    @DisplayName("GET /fuel-prices/average - Should return default R$ 6.00 when no data")
    void testObterPrecoMedio_DefaultPrice() throws Exception {
        LocalDate data = LocalDate.now().minusYears(10); // Very old date with no data

        mockMvc.perform(get("/v1/tenants/{tenantId}/fuel-prices/average", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .param("data", data.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string("6.00")); // Default fallback price
    }

    @Test
    @DisplayName("GET /fuel-prices/average - Should return 400 when data parameter missing")
    void testObterPrecoMedio_MissingDataParam() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/fuel-prices/average", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isBadRequest());
    }

    // ===================================================================
    // Security tests
    // ===================================================================

    @Test
    @DisplayName("Should return 401 when no authentication")
    void testCriarPreco_NoAuth() throws Exception {
        FuelPriceDayCreateRequest request = FuelPriceDayCreateRequest.builder()
            .data(LocalDate.now())
            .precoMedioLitro(new BigDecimal("7.50"))
            .totalLitrosAbastecidos(BigDecimal.ZERO)
            .totalCusto(BigDecimal.ZERO)
            .qtdAbastecimentos(0)
            .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/fuel-prices", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 403 when tenant mismatch")
    void testCriarPreco_TenantMismatch() throws Exception {
        UUID differentTenantId = UUID.randomUUID();

        FuelPriceDayCreateRequest request = FuelPriceDayCreateRequest.builder()
            .data(LocalDate.now())
            .precoMedioLitro(new BigDecimal("7.50"))
            .totalLitrosAbastecidos(BigDecimal.ZERO)
            .totalCusto(BigDecimal.ZERO)
            .qtdAbastecimentos(0)
            .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/fuel-prices", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", differentTenantId.toString()) // Different tenant!
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden()); // 403 - Authorization denied by TenantFilter
    }
}
