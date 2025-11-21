package com.jetski.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.fechamento.api.dto.ConsolidarDiaRequest;
import com.jetski.fechamento.api.dto.ConsolidarMesRequest;
import com.jetski.locacoes.domain.Locacao;
import com.jetski.locacoes.domain.LocacaoStatus;
import com.jetski.comissoes.domain.Comissao;
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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Fechamento API (FechamentoController)
 *
 * Tests daily and monthly closure endpoints with database and Spring context
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@AutoConfigureMockMvc
@DisplayName("Integration: Fechamento API")
class FechamentoControllerIntegrationTest extends AbstractIntegrationTest {

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

    @MockBean
    private com.jetski.usuarios.api.UsuarioService usuarioService;

    @MockBean
    private com.jetski.locacoes.api.LocacaoQueryService locacaoQueryService;

    @MockBean
    private com.jetski.comissoes.api.ComissaoQueryService comissaoQueryService;

    private static final UUID TENANT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MODELO_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID JETSKI_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CLIENTE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID VENDEDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        ensureTestEntitiesExist();

        // Mock tenant access
        when(tenantAccessService.validateAccess(any(String.class), eq(USER_ID.toString()), any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID requestedTenantId = invocation.getArgument(2, UUID.class);
                if (requestedTenantId.equals(TENANT_ID)) {
                    return TenantAccessInfo.builder()
                        .hasAccess(true)
                        .roles(List.of("GERENTE", "ADMIN_TENANT", "FINANCEIRO"))
                        .unrestricted(false)
                        .usuarioId(USER_ID)
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

        // Mock UsuarioService to return USER_ID for any Authentication
        when(usuarioService.getUserIdFromAuthentication(any()))
            .thenReturn(USER_ID);

        // Mock LocacaoQueryService to return empty list by default
        when(locacaoQueryService.findByTenantIdAndDateRange(any(UUID.class), any(), any()))
            .thenReturn(List.of());

        // Mock ComissaoQueryService to return empty list by default
        when(comissaoQueryService.findByPeriodo(any(UUID.class), any(), any()))
            .thenReturn(List.of());
    }

    /**
     * Ensure test entities exist in database (idempotent)
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
            VALUES (?, ?, ?, 'JET-FECH-001', 'FECH-1234', 2023, 45.2, 'disponivel', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, JETSKI_ID, TENANT_ID, MODELO_ID);

        // Cliente
        jdbcTemplate.update("""
            INSERT INTO cliente (id, tenant_id, nome, documento, email, telefone, ativo)
            VALUES (?, ?, 'Cliente Teste Fechamento', '12345678901', 'cliente.fechamento@test.com', '11999999999', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, CLIENTE_ID, TENANT_ID);

        // Vendedor
        jdbcTemplate.update("""
            INSERT INTO vendedor (id, tenant_id, nome, tipo, ativo)
            VALUES (?, ?, 'Vendedor Teste Fechamento', 'interno', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, VENDEDOR_ID, TENANT_ID);

        // Usuario autenticado (usado nos testes com JWT)
        jdbcTemplate.update("""
            INSERT INTO usuario (id, email, nome, ativo)
            VALUES (?, ?, 'Gerente Teste', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, USER_ID, USER_ID.toString());

        jdbcTemplate.update("""
            INSERT INTO membro (id, tenant_id, usuario_id, papeis)
            VALUES (999993, ?, ?, ARRAY['GERENTE', 'ADMIN_TENANT', 'FINANCEIRO'])
            ON CONFLICT (tenant_id, usuario_id) DO NOTHING
            """, TENANT_ID, USER_ID);
    }

    // ===================================================================
    // Fechamento Diário - Tests
    // ===================================================================

    @Test
    @DisplayName("Should consolidate daily closure successfully")
    void testConsolidarDia_Success() throws Exception {
        // Given
        LocalDate dataReferencia = LocalDate.now().minusDays(1);

        // Don't create locacoes directly - the service will call the mocked query service
        // which returns empty lists by default (configured in setUp())

        ConsolidarDiaRequest request = ConsolidarDiaRequest.builder()
            .dtReferencia(dataReferencia)
            .build();

        // When / Then
        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/fechamentos/dia/consolidar")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.dtReferencia").value(dataReferencia.toString()))
            .andExpect(jsonPath("$.operadorId").value(USER_ID.toString()))
            .andExpect(jsonPath("$.status").value("aberto"))
            .andExpect(jsonPath("$.bloqueado").value(false))
            .andExpect(jsonPath("$.totalLocacoes").isNumber())
            .andExpect(jsonPath("$.totalFaturado").isNumber());
    }

    @Test
    @DisplayName("Should fetch daily closure by ID")
    void testBuscarFechamentoDiarioPorId_Success() throws Exception {
        // Given
        LocalDate dataReferencia = LocalDate.now().minusDays(2);
        UUID fechamentoId = createTestFechamentoDiario(dataReferencia);

        // When / Then
        mockMvc.perform(get("/v1/tenants/" + TENANT_ID + "/fechamentos/dia/" + fechamentoId)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(fechamentoId.toString()))
            .andExpect(jsonPath("$.dtReferencia").value(dataReferencia.toString()))
            .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("Should fetch daily closure by date")
    void testBuscarFechamentoDiarioPorData_Success() throws Exception {
        // Given
        LocalDate dataReferencia = LocalDate.now().minusDays(3);
        createTestFechamentoDiario(dataReferencia);

        // When / Then
        mockMvc.perform(get("/v1/tenants/" + TENANT_ID + "/fechamentos/dia/data/" + dataReferencia)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dtReferencia").value(dataReferencia.toString()))
            .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("Should list daily closures by date range")
    void testListarFechamentosDiarios_Success() throws Exception {
        // Given
        LocalDate dataInicio = LocalDate.now().minusDays(10);
        LocalDate dataFim = LocalDate.now().minusDays(1);

        createTestFechamentoDiario(LocalDate.now().minusDays(5));
        createTestFechamentoDiario(LocalDate.now().minusDays(6));

        // When / Then
        mockMvc.perform(get("/v1/tenants/" + TENANT_ID + "/fechamentos/dia")
                .param("dataInicio", dataInicio.toString())
                .param("dataFim", dataFim.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Should close daily closure")
    void testFecharDia_Success() throws Exception {
        // Given
        LocalDate dataReferencia = LocalDate.now().minusDays(4);
        UUID fechamentoId = createTestFechamentoDiario(dataReferencia);

        // When / Then
        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/fechamentos/dia/" + fechamentoId + "/fechar")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(fechamentoId.toString()))
            .andExpect(jsonPath("$.status").value("fechado"))
            .andExpect(jsonPath("$.bloqueado").value(true))
            .andExpect(jsonPath("$.dtFechamento").exists());
    }

    @Test
    @DisplayName("Should approve daily closure")
    void testAprovarFechamentoDiario_Success() throws Exception {
        // Given
        LocalDate dataReferencia = LocalDate.now().minusDays(5);
        UUID fechamentoId = createTestFechamentoDiario(dataReferencia);

        // First close it via API (not JDBC)
        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/fechamentos/dia/" + fechamentoId + "/fechar")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk());

        // When / Then
        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/fechamentos/dia/" + fechamentoId + "/aprovar")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(fechamentoId.toString()))
            .andExpect(jsonPath("$.status").value("aprovado"));
    }

    @Test
    @DisplayName("Should reopen daily closure")
    void testReabrirFechamentoDiario_Success() throws Exception {
        // Given
        LocalDate dataReferencia = LocalDate.now().minusDays(6);
        UUID fechamentoId = createTestFechamentoDiario(dataReferencia);

        // First close it via API (not JDBC)
        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/fechamentos/dia/" + fechamentoId + "/fechar")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk());

        // When / Then
        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/fechamentos/dia/" + fechamentoId + "/reabrir")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(fechamentoId.toString()))
            .andExpect(jsonPath("$.status").value("aberto"))
            .andExpect(jsonPath("$.bloqueado").value(false));
    }

    @Test
    @DisplayName("Should not reopen approved daily closure")
    void testReabrirFechamentoDiario_Aprovado_Fail() throws Exception {
        // Given
        LocalDate dataReferencia = LocalDate.now().minusDays(7);
        UUID fechamentoId = createTestFechamentoDiario(dataReferencia);

        // First close it via API
        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/fechamentos/dia/" + fechamentoId + "/fechar")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk());

        // Then approve it via API
        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/fechamentos/dia/" + fechamentoId + "/aprovar")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk());

        // When / Then - Try to reopen approved closure (should fail)
        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/fechamentos/dia/" + fechamentoId + "/reabrir")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("aprovado")));
    }

    // ===================================================================
    // Fechamento Mensal - Tests
    // ===================================================================

    @Test
    @DisplayName("Should consolidate monthly closure successfully")
    void testConsolidarMes_Success() throws Exception {
        // Given
        int ano = LocalDate.now().getYear();
        int mes = LocalDate.now().minusMonths(1).getMonthValue();

        // Create some daily closures for this month
        LocalDate primeiroDia = LocalDate.of(ano, mes, 1);
        createTestFechamentoDiario(primeiroDia);
        createTestFechamentoDiario(primeiroDia.plusDays(1));

        ConsolidarMesRequest request = ConsolidarMesRequest.builder()
            .ano(ano)
            .mes(mes)
            .build();

        // When / Then
        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/fechamentos/mes/consolidar")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.ano").value(ano))
            .andExpect(jsonPath("$.mes").value(mes))
            .andExpect(jsonPath("$.operadorId").value(USER_ID.toString()))
            .andExpect(jsonPath("$.status").value("aberto"))
            .andExpect(jsonPath("$.bloqueado").value(false))
            .andExpect(jsonPath("$.totalLocacoes").isNumber())
            .andExpect(jsonPath("$.totalFaturado").isNumber())
            .andExpect(jsonPath("$.resultadoLiquido").isNumber());
    }

    @Test
    @DisplayName("Should fetch monthly closure by ID")
    void testBuscarFechamentoMensalPorId_Success() throws Exception {
        // Given
        int ano = 2025;
        int mes = 1;
        UUID fechamentoId = createTestFechamentoMensal(ano, mes);

        // When / Then
        mockMvc.perform(get("/v1/tenants/" + TENANT_ID + "/fechamentos/mes/" + fechamentoId)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(fechamentoId.toString()))
            .andExpect(jsonPath("$.ano").value(ano))
            .andExpect(jsonPath("$.mes").value(mes))
            .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("Should fetch monthly closure by period")
    void testBuscarFechamentoMensalPorPeriodo_Success() throws Exception {
        // Given
        int ano = 2025;
        int mes = 2;
        createTestFechamentoMensal(ano, mes);

        // When / Then
        mockMvc.perform(get("/v1/tenants/" + TENANT_ID + "/fechamentos/mes/" + ano + "/" + mes)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ano").value(ano))
            .andExpect(jsonPath("$.mes").value(mes))
            .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("Should list all monthly closures")
    void testListarFechamentosMensais_Success() throws Exception {
        // Given
        createTestFechamentoMensal(2025, 3);
        createTestFechamentoMensal(2025, 4);

        // When / Then
        mockMvc.perform(get("/v1/tenants/" + TENANT_ID + "/fechamentos/mes")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Should list monthly closures by year")
    void testListarFechamentosMensaisPorAno_Success() throws Exception {
        // Given
        int ano = 2024;
        createTestFechamentoMensal(ano, 11);
        createTestFechamentoMensal(ano, 12);

        // When / Then
        mockMvc.perform(get("/v1/tenants/" + TENANT_ID + "/fechamentos/mes/ano/" + ano)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Should close monthly closure")
    void testFecharMes_Success() throws Exception {
        // Given
        int ano = 2025;
        int mes = 5;
        UUID fechamentoId = createTestFechamentoMensal(ano, mes);

        // When / Then
        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/fechamentos/mes/" + fechamentoId + "/fechar")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(fechamentoId.toString()))
            .andExpect(jsonPath("$.status").value("fechado"))
            .andExpect(jsonPath("$.bloqueado").value(true))
            .andExpect(jsonPath("$.dtFechamento").exists());
    }

    @Test
    @DisplayName("Should approve monthly closure")
    void testAprovarFechamentoMensal_Success() throws Exception {
        // Given
        int ano = 2025;
        int mes = 6;
        UUID fechamentoId = createTestFechamentoMensal(ano, mes);

        // First close it
        jdbcTemplate.update("""
            UPDATE fechamento_mensal
            SET status = 'fechado', bloqueado = true, dt_fechamento = ?
            WHERE id = ?
            """, Timestamp.from(Instant.now()), fechamentoId);

        // When / Then
        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/fechamentos/mes/" + fechamentoId + "/aprovar")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(fechamentoId.toString()))
            .andExpect(jsonPath("$.status").value("aprovado"));
    }

    @Test
    @DisplayName("Should reopen monthly closure")
    void testReabrirFechamentoMensal_Success() throws Exception {
        // Given
        int ano = 2025;
        int mes = 7;
        UUID fechamentoId = createTestFechamentoMensal(ano, mes);

        // First close it
        jdbcTemplate.update("""
            UPDATE fechamento_mensal
            SET status = 'fechado', bloqueado = true, dt_fechamento = ?
            WHERE id = ?
            """, Timestamp.from(Instant.now()), fechamentoId);

        // When / Then
        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/fechamentos/mes/" + fechamentoId + "/reabrir")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(fechamentoId.toString()))
            .andExpect(jsonPath("$.status").value("aberto"))
            .andExpect(jsonPath("$.bloqueado").value(false));
    }

    // ===================================================================
    // Helper Methods
    // ===================================================================

    private UUID createTestFechamentoDiario(LocalDate dataReferencia) {
        UUID fechamentoId = UUID.randomUUID();

        // Delete any existing fechamento for this date first
        jdbcTemplate.update("""
            DELETE FROM fechamento_diario
            WHERE tenant_id = ? AND dt_referencia = ?
            """, TENANT_ID, dataReferencia);

        jdbcTemplate.update("""
            INSERT INTO fechamento_diario (id, tenant_id, dt_referencia, operador_id,
                                           total_locacoes, total_faturado, total_combustivel,
                                           total_comissoes, total_dinheiro, total_cartao, total_pix,
                                           status, bloqueado, created_at, updated_at)
            VALUES (?, ?, ?, ?, 2, 800.00, 50.00, 80.00, 300.00, 400.00, 100.00, 'aberto', FALSE, NOW(), NOW())
            """, fechamentoId, TENANT_ID, dataReferencia, USER_ID);
        return fechamentoId;
    }

    private UUID createTestFechamentoMensal(int ano, int mes) {
        UUID fechamentoId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO fechamento_mensal (id, tenant_id, ano, mes, operador_id,
                                           total_locacoes, total_faturado, total_custos,
                                           total_comissoes, total_manutencoes, resultado_liquido,
                                           status, bloqueado, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 50, 15000.00, 3000.00, 1500.00, 500.00, 10000.00, 'aberto', FALSE, NOW(), NOW())
            ON CONFLICT (tenant_id, ano, mes) DO NOTHING
            """, fechamentoId, TENANT_ID, ano, mes, USER_ID);
        return fechamentoId;
    }

    private void createTestLocacao(LocalDate dataReferencia, BigDecimal valorTotal, String status) {
        UUID locacaoId = UUID.randomUUID();
        Instant dtCheckIn = dataReferencia.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        Instant dtCheckOut = dtCheckIn.plus(2, ChronoUnit.HOURS);

        jdbcTemplate.update("""
            INSERT INTO locacao (id, tenant_id, jetski_id, cliente_id, vendedor_id,
                                data_check_in, data_check_out, horimetro_inicio, horimetro_fim,
                                duracao_prevista, minutos_usados, minutos_faturaveis,
                                valor_base, valor_total, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, 100.0, 102.0, 120, 120, 120, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """, locacaoId, TENANT_ID, JETSKI_ID, CLIENTE_ID, VENDEDOR_ID,
                Timestamp.from(dtCheckIn), Timestamp.from(dtCheckOut),
                valorTotal, valorTotal, status);
    }
}
