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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    @MockBean
    private TenantAccessService tenantAccessService;

    private static final UUID TENANT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID JETSKI_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID LOCACAO_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID MODELO_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CLIENTE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @BeforeEach
    @Transactional
    void setUp() {
        // Ensure test entities exist (idempotent - INSERT ... ON CONFLICT DO NOTHING)
        ensureTestEntitiesExist();

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

    /**
     * Ensure test entities exist in database (idempotent)
     * This guarantees data integrity when running full test suite
     */
    private void ensureTestEntitiesExist() {
        // Modelo
        jdbcTemplate.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'SeaDoo GTI SE 130', 'Sea-Doo', 130, 2, 150.00, 5, 50.00, 300.00, FALSE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, MODELO_ID, TENANT_ID);

        // Jetski
        jdbcTemplate.update("""
            INSERT INTO jetski (id, tenant_id, modelo_id, serie, placa, ano, horimetro_atual, status, ativo)
            VALUES (?, ?, ?, 'JET-TEST-001', 'ABC-1234', 2023, 45.2, 'disponivel', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, JETSKI_ID, TENANT_ID, MODELO_ID);

        // Cliente
        jdbcTemplate.update("""
            INSERT INTO cliente (id, tenant_id, nome, documento, email, telefone, termo_aceite, ativo)
            VALUES (?, ?, 'Cliente Teste', '123.456.789-00', 'cliente.teste@example.com', '+55 11 98765-4321', TRUE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, CLIENTE_ID, TENANT_ID);

        // Locacao
        jdbcTemplate.update("""
            INSERT INTO locacao (id, tenant_id, jetski_id, cliente_id, data_check_in, horimetro_inicio,
                                 duracao_prevista, valor_base, valor_total, status)
            VALUES (?, ?, ?, ?, NOW() - INTERVAL '2 hours', 40.0, 180, 150.00, 150.00, 'EM_CURSO')
            ON CONFLICT (id) DO NOTHING
            """, LOCACAO_ID, TENANT_ID, JETSKI_ID, CLIENTE_ID);
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
