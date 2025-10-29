package com.jetski.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.combustivel.api.dto.AbastecimentoCreateRequest;
import com.jetski.combustivel.domain.TipoAbastecimento;
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
import java.time.Instant;
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
 * Integration tests for AbastecimentoController
 *
 * Tests fuel refill management API endpoints with database and Spring context
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@AutoConfigureMockMvc
@DisplayName("Integration: AbastecimentoController")
class AbastecimentoControllerIntegrationTest extends AbstractIntegrationTest {

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
    private static final UUID LOCACAO_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        // Mock tenant access with conditional logic for tenant matching
        when(tenantAccessService.validateAccess(any(String.class), eq(USER_ID.toString()), any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID requestedTenantId = invocation.getArgument(2, UUID.class);
                if (requestedTenantId.equals(TENANT_ID)) {
                    return TenantAccessInfo.builder()
                        .hasAccess(true)
                        .roles(List.of("OPERADOR", "GERENTE"))
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
    // POST /v1/tenants/{tenantId}/abastecimentos - Register refill
    // ===================================================================

    @Test
    @DisplayName("POST /abastecimentos - Should register PRE_LOCACAO refill")
    void testRegistrarAbastecimento_PreLocacao() throws Exception {
        AbastecimentoCreateRequest request = AbastecimentoCreateRequest.builder()
            .jetskiId(JETSKI_ID)
            .locacaoId(LOCACAO_ID)
            .tipo(TipoAbastecimento.PRE_LOCACAO)
            .litros(new BigDecimal("50.00"))
            .precoLitro(new BigDecimal("7.00"))
            .custoTotal(new BigDecimal("350.00"))
            .dataHora(Instant.now())
            .observacoes("Abastecimento antes da locação")
            .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/abastecimentos", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
            .andExpect(jsonPath("$.jetskiId").value(JETSKI_ID.toString()))
            .andExpect(jsonPath("$.locacaoId").value(LOCACAO_ID.toString()))
            .andExpect(jsonPath("$.tipo").value("PRE_LOCACAO"))
            .andExpect(jsonPath("$.litros").value(50.00))
            .andExpect(jsonPath("$.precoLitro").value(7.00))
            .andExpect(jsonPath("$.custoTotal").value(350.00))
            .andExpect(jsonPath("$.responsavelId").value(USER_ID.toString()));
    }

    @Test
    @DisplayName("POST /abastecimentos - Should register FROTA refill without locacao")
    void testRegistrarAbastecimento_Frota() throws Exception{
        AbastecimentoCreateRequest request = AbastecimentoCreateRequest.builder()
            .jetskiId(JETSKI_ID)
            .locacaoId(null) // FROTA doesn't require locacao
            .tipo(TipoAbastecimento.FROTA)
            .litros(new BigDecimal("40.00"))
            .precoLitro(new BigDecimal("7.20"))
            .custoTotal(new BigDecimal("288.00"))
            .dataHora(Instant.now())
            .observacoes("Abastecimento geral da frota")
            .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/abastecimentos", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tipo").value("FROTA"))
            .andExpect(jsonPath("$.locacaoId").doesNotExist());
    }

    @Test
    @DisplayName("POST /abastecimentos - Should return 400 for missing required fields")
    void testRegistrarAbastecimento_ValidationError() throws Exception {
        AbastecimentoCreateRequest request = AbastecimentoCreateRequest.builder()
            .jetskiId(null) // Missing required field
            .tipo(TipoAbastecimento.PRE_LOCACAO)
            .litros(new BigDecimal("50.00"))
            .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/abastecimentos", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    // ===================================================================
    // GET /v1/tenants/{tenantId}/abastecimentos - List refills
    // ===================================================================

    @Test
    @DisplayName("GET /abastecimentos - Should list all refills")
    void testListarAbastecimentos() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/abastecimentos", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /abastecimentos - Should filter by tipo")
    void testListarAbastecimentos_FilterByTipo() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/abastecimentos", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .param("tipo", "PRE_LOCACAO"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ===================================================================
    // GET /v1/tenants/{tenantId}/abastecimentos/{id} - Get by ID
    // ===================================================================

    @Test
    @DisplayName("GET /abastecimentos/{id} - Should return 404 for non-existent refill")
    void testBuscarAbastecimento_NotFound() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/abastecimentos/{id}", TENANT_ID, 99999L)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isNotFound());
    }

    // ===================================================================
    // GET /v1/tenants/{tenantId}/abastecimentos/jetski/{jetskiId} - List by jetski
    // ===================================================================

    @Test
    @DisplayName("GET /abastecimentos/jetski/{jetskiId} - Should list refills by jetski")
    void testListarPorJetski() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/abastecimentos/jetski/{jetskiId}",
                    TENANT_ID, JETSKI_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ===================================================================
    // GET /v1/tenants/{tenantId}/abastecimentos/locacao/{locacaoId} - List by locacao
    // ===================================================================

    @Test
    @DisplayName("GET /abastecimentos/locacao/{locacaoId} - Should list refills by locacao")
    void testListarPorLocacao() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/abastecimentos/locacao/{locacaoId}",
                    TENANT_ID, LOCACAO_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ===================================================================
    // Security tests
    // ===================================================================

    @Test
    @DisplayName("Should return 401 when no authentication")
    void testRegistrarAbastecimento_NoAuth() throws Exception {
        AbastecimentoCreateRequest request = AbastecimentoCreateRequest.builder()
            .jetskiId(JETSKI_ID)
            .tipo(TipoAbastecimento.FROTA)
            .litros(new BigDecimal("50.00"))
            .precoLitro(new BigDecimal("7.00"))
            .custoTotal(new BigDecimal("350.00"))
            .dataHora(Instant.now())
            .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/abastecimentos", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 403 when tenant mismatch")
    void testRegistrarAbastecimento_TenantMismatch() throws Exception {
        UUID differentTenantId = UUID.randomUUID();

        AbastecimentoCreateRequest request = AbastecimentoCreateRequest.builder()
            .jetskiId(JETSKI_ID)
            .tipo(TipoAbastecimento.FROTA)
            .litros(new BigDecimal("50.00"))
            .precoLitro(new BigDecimal("7.00"))
            .custoTotal(new BigDecimal("350.00"))
            .dataHora(Instant.now())
            .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/abastecimentos", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                .header("X-Tenant-Id", differentTenantId.toString()) // Different tenant!
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden()); // 403 - Authorization denied by TenantFilter
    }
}
