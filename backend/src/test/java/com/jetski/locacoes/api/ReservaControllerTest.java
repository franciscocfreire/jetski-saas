package com.jetski.locacoes.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.locacoes.api.dto.ReservaCreateRequest;
import com.jetski.locacoes.api.dto.ReservaUpdateRequest;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.JetskiStatus;
import com.jetski.locacoes.domain.Modelo;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.Reserva.ReservaStatus;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.JetskiRepository;
import com.jetski.locacoes.internal.repository.ModeloRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
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
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ReservaController
 *
 * Tests reservation/booking operations:
 * - List reservations (all, filtered by status)
 * - Get reservation by ID
 * - Create reservation
 * - Conflict detection (schedule overlaps)
 * - Jetski availability validation (RN06)
 * - Update reservation
 * - Confirm reservation (PENDENTE → CONFIRMADA)
 * - Cancel reservation
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@AutoConfigureMockMvc
@DisplayName("ReservaController Tests")
class ReservaControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private com.jetski.locacoes.internal.repository.ReservaLancamentoRepository reservaLancamentoRepository;

    @Autowired
    private JetskiRepository jetskiRepository;

    @Autowired
    private ModeloRepository modeloRepository;

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

    private Modelo testModelo;
    private Jetski testJetski;
    private Cliente testCliente;
    private Reserva testReserva;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);

        // Clean up dependent tables first (from V999 seed data) - in correct FK dependency order
        jdbcTemplate.execute("DELETE FROM os_manutencao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM locacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM politica_comissao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM fuel_policy WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM jetski WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM modelo WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM cliente WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");

        // Ensure test user has identity provider mapping (needed for authentication filter)
        try {
            jdbcTemplate.execute("INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id, linked_at) " +
                                 "VALUES ('11111111-1111-1111-1111-111111111111', 'keycloak', '11111111-1111-1111-1111-111111111111', NOW())");
        } catch (Exception ignored) {
            // Ignore if already exists
        }

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
                .nome("Yamaha VX Cruiser")
                .fabricante("Yamaha")
                .precoBaseHora(new BigDecimal("300.00"))
                .ativo(true)
                .build();
        testModelo = modeloRepository.save(testModelo);

        // Create test jetski (DISPONIVEL status)
        testJetski = Jetski.builder()
                .tenantId(TENANT_ID)
                .modeloId(testModelo.getId())
                .serie("YMH-2024-001")
                .ano(2024)
                .horimetroAtual(new BigDecimal("150.5"))
                .status(JetskiStatus.DISPONIVEL)
                .ativo(true)
                .build();
        testJetski = jetskiRepository.save(testJetski);

        // Create test cliente
        testCliente = Cliente.builder()
                .tenantId(TENANT_ID)
                .nome("Carlos Teste")
                .documento("123.456.789-00")
                .dataNascimento(java.time.LocalDate.of(1985, 6, 20))
                .genero("MASCULINO")
                .email("carlos.teste@email.com")
                .telefone("+5511987654321")
                .termoAceite(true)
                .ativo(true)
                .build();
        testCliente = clienteRepository.save(testCliente);

        // Create test reservation (confirmed, tomorrow 10:00-12:00)
        LocalDateTime tomorrow10am = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime tomorrow12pm = tomorrow10am.plusHours(2);

        testReserva = Reserva.builder()
                .tenantId(TENANT_ID)
                .modeloId(testModelo.getId())
                .jetskiId(testJetski.getId())
                .clienteId(testCliente.getId())
                .dataInicio(tomorrow10am)
                .dataFimPrevista(tomorrow12pm)
                .status(ReservaStatus.CONFIRMADA)
                .observacoes("Reserva de teste")
                .ativo(true)
                .build();
        testReserva = reservaRepository.save(testReserva);
    }

    @AfterEach
    void tearDown() {
        reservaRepository.deleteAll();
        jetskiRepository.deleteAll();
        modeloRepository.deleteAll();
        clienteRepository.deleteAll();
        TenantContext.clear();
    }

    // ========================================================================
    // Happy Path Tests
    // ========================================================================

    @Test
    @DisplayName("Should list active reservations for tenant")
    void testListReservas_ActiveOnly() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/reservas", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(testReserva.getId().toString()))
            .andExpect(jsonPath("$[0].jetskiId").value(testJetski.getId().toString()))
            .andExpect(jsonPath("$[0].clienteId").value(testCliente.getId().toString()))
            .andExpect(jsonPath("$[0].status").value("CONFIRMADA"))
            .andExpect(jsonPath("$[0].ativo").value(true));
    }

    @Test
    @DisplayName("Should filter reservations by status")
    void testListReservas_FilterByStatus() throws Exception {
        // Create a pending reservation
        LocalDateTime dayAfterTomorrow = LocalDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0);
        Reserva pendingReserva = Reserva.builder()
                .tenantId(TENANT_ID)
                .modeloId(testModelo.getId())
                .jetskiId(testJetski.getId())
                .clienteId(testCliente.getId())
                .dataInicio(dayAfterTomorrow)
                .dataFimPrevista(dayAfterTomorrow.plusHours(1))
                .status(ReservaStatus.PENDENTE)
                .ativo(true)
                .build();
        reservaRepository.save(pendingReserva);

        mockMvc.perform(get("/v1/tenants/{tenantId}/reservas?status=PENDENTE", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].status").value("PENDENTE"));
    }

    @Test
    @DisplayName("Should get reservation by ID")
    void testGetReserva_ById() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/reservas/{id}", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testReserva.getId().toString()))
            .andExpect(jsonPath("$.jetskiId").value(testJetski.getId().toString()))
            .andExpect(jsonPath("$.clienteId").value(testCliente.getId().toString()))
            .andExpect(jsonPath("$.status").value("CONFIRMADA"));
    }

    @Test
    @DisplayName("Should create new reservation")
    void testCreateReserva() throws Exception {
        // Schedule for next week to avoid conflict
        LocalDateTime nextWeek = LocalDateTime.now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0);

        ReservaCreateRequest request = ReservaCreateRequest.builder()
                .modeloId(testModelo.getId())
                .jetskiId(testJetski.getId())
                .clienteId(testCliente.getId())
                .dataInicio(nextWeek)
                .dataFimPrevista(nextWeek.plusHours(2))
                .observacoes("Nova reserva de teste")
                .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.jetskiId").value(testJetski.getId().toString()))
            .andExpect(jsonPath("$.clienteId").value(testCliente.getId().toString()))
            .andExpect(jsonPath("$.status").value("PENDENTE"))
            .andExpect(jsonPath("$.observacoes").value("Nova reserva de teste"))
            .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("Should update existing reservation")
    void testUpdateReserva() throws Exception {
        // Create a pending reservation first
        LocalDateTime future = LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);
        Reserva pendingReserva = Reserva.builder()
                .tenantId(TENANT_ID)
                .modeloId(testModelo.getId())
                .jetskiId(testJetski.getId())
                .clienteId(testCliente.getId())
                .dataInicio(future)
                .dataFimPrevista(future.plusHours(1))
                .status(ReservaStatus.PENDENTE)
                .observacoes("Original")
                .ativo(true)
                .build();
        pendingReserva = reservaRepository.save(pendingReserva);

        ReservaUpdateRequest request = ReservaUpdateRequest.builder()
                .dataFimPrevista(future.plusHours(2))  // Extend 1 hour
                .observacoes("Atualizada - mais 1 hora")
                .build();

        mockMvc.perform(put("/v1/tenants/{tenantId}/reservas/{id}", TENANT_ID, pendingReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(pendingReserva.getId().toString()))
            .andExpect(jsonPath("$.observacoes").value("Atualizada - mais 1 hora"));
    }

    @Test
    @DisplayName("Should confirm pending reservation")
    void testConfirmReserva() throws Exception {
        // Create a pending reservation
        LocalDateTime future = LocalDateTime.now().plusDays(5).withHour(14).withMinute(0).withSecond(0).withNano(0);
        Reserva pendingReserva = Reserva.builder()
                .tenantId(TENANT_ID)
                .modeloId(testModelo.getId())
                .jetskiId(testJetski.getId())
                .clienteId(testCliente.getId())
                .dataInicio(future)
                .dataFimPrevista(future.plusHours(1))
                .status(ReservaStatus.PENDENTE)
                .ativo(true)
                .build();
        pendingReserva = reservaRepository.save(pendingReserva);

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/confirmar", TENANT_ID, pendingReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(pendingReserva.getId().toString()))
            .andExpect(jsonPath("$.status").value("CONFIRMADA"));
    }

    @Test
    @DisplayName("Should cancel reservation")
    void testCancelReserva() throws Exception {
        mockMvc.perform(delete("/v1/tenants/{tenantId}/reservas/{id}", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testReserva.getId().toString()))
            .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    // ========================================================================
    // Business Rule Tests
    // ========================================================================

    @Test
    @DisplayName("Should reject reservation with schedule conflict")
    void testCreateReserva_ScheduleConflict() throws Exception {
        // Try to reserve same jetski for overlapping period
        LocalDateTime conflictStart = testReserva.getDataInicio().plusMinutes(30);  // Overlaps existing reservation

        ReservaCreateRequest request = ReservaCreateRequest.builder()
                .modeloId(testModelo.getId())
                .jetskiId(testJetski.getId())
                .clienteId(testCliente.getId())
                .dataInicio(conflictStart)
                .dataFimPrevista(conflictStart.plusHours(1))
                .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject reservation for jetski in maintenance (RN06)")
    void testCreateReserva_JetskiInMaintenance() throws Exception {
        // Update jetski status to MANUTENCAO
        testJetski.setStatus(JetskiStatus.MANUTENCAO);
        jetskiRepository.save(testJetski);

        LocalDateTime future = LocalDateTime.now().plusDays(10).withHour(10).withMinute(0).withSecond(0).withNano(0);

        ReservaCreateRequest request = ReservaCreateRequest.builder()
                .modeloId(testModelo.getId())
                .jetskiId(testJetski.getId())
                .clienteId(testCliente.getId())
                .dataInicio(future)
                .dataFimPrevista(future.plusHours(1))
                .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject reservation with end date before start date")
    void testCreateReserva_InvalidDateRange() throws Exception {
        LocalDateTime future = LocalDateTime.now().plusDays(5).withHour(14).withMinute(0).withSecond(0).withNano(0);

        ReservaCreateRequest request = ReservaCreateRequest.builder()
                .modeloId(testModelo.getId())
                .jetskiId(testJetski.getId())
                .clienteId(testCliente.getId())
                .dataInicio(future)
                .dataFimPrevista(future.minusHours(1))  // End before start!
                .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isBadRequest());
    }

    // ========================================================================
    // Error Cases
    // ========================================================================

    @Test
    @DisplayName("Should return 400 when reservation not found")
    void testGetReserva_NotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/v1/tenants/{tenantId}/reservas/{id}", TENANT_ID, nonExistentId)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 401 for unauthenticated request")
    void testListReservas_Unauthorized() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/reservas", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 400 for invalid create request")
    void testCreateReserva_InvalidRequest() throws Exception {
        ReservaCreateRequest request = ReservaCreateRequest.builder()
                .jetskiId(null)  // Invalid: missing jetski
                .clienteId(null)  // Invalid: missing cliente
                .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should not allow updating cancelled reservation")
    void testUpdateReserva_CancelledReservation() throws Exception {
        // Cancel the test reservation
        testReserva.setStatus(ReservaStatus.CANCELADA);
        reservaRepository.save(testReserva);

        ReservaUpdateRequest request = ReservaUpdateRequest.builder()
                .observacoes("Tentativa de atualizar cancelada")
                .build();

        mockMvc.perform(put("/v1/tenants/{tenantId}/reservas/{id}", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should confirm deposit payment and upgrade to ALTA priority")
    void testConfirmarSinal_Success() throws Exception {
        String requestBody = "{\"valorSinal\": 150.00}";

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/confirmar-sinal", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sinalPago").value(true))
            .andExpect(jsonPath("$.prioridade").value("ALTA"))
            .andExpect(jsonPath("$.valorSinal").value(150.00));
    }

    @Test
    @DisplayName("Should allow FINANCEIRO to confirm deposit payment (F1.G)")
    void testConfirmarSinal_AllowedForFinanceiro() throws Exception {
        String requestBody = "{\"valorSinal\": 150.00}";

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/confirmar-sinal", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_FINANCEIRO"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.prioridade").value("ALTA"));
    }

    @Test
    @DisplayName("Should reject confirmar-sinal for MECANICO (no permission)")
    void testConfirmarSinal_ForbiddenForMecanico() throws Exception {
        String requestBody = "{\"valorSinal\": 150.00}";

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/confirmar-sinal", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MECANICO"))))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should register presential payment: CONFIRMADO/TOTAL/ALTA + lançamento no ledger")
    void testRegistrarPagamentoPresencial_Success() throws Exception {
        String requestBody = "{\"forma\": \"DINHEIRO\", \"valor\": 600.00, \"observacao\": \"pago no balcão\"}";

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/registrar-pagamento", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagamentoStatus").value("CONFIRMADO"))
            .andExpect(jsonPath("$.pagamentoTipo").value("TOTAL"))
            .andExpect(jsonPath("$.prioridade").value("ALTA"))
            .andExpect(jsonPath("$.valorTotal").value(600.00));

        var lancamentos = reservaLancamentoRepository.findByReservaIdOrderByCreatedAtAsc(testReserva.getId());
        org.assertj.core.api.Assertions.assertThat(lancamentos).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(lancamentos.get(0).getTipo())
            .isEqualTo(com.jetski.locacoes.domain.ReservaLancamento.Tipo.PAGAMENTO);
        org.assertj.core.api.Assertions.assertThat(lancamentos.get(0).getForma())
            .isEqualTo(com.jetski.locacoes.domain.ReservaLancamento.Forma.DINHEIRO);
        org.assertj.core.api.Assertions.assertThat(lancamentos.get(0).getValor())
            .isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("Should reject invalid forma de pagamento (400)")
    void testRegistrarPagamentoPresencial_FormaInvalida() throws Exception {
        String requestBody = "{\"forma\": \"CHEQUE\", \"valor\": 600.00}";

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/registrar-pagamento", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject presential payment when already paid")
    void testRegistrarPagamentoPresencial_JaPago() throws Exception {
        // CHECKs reserva_prioridade_sinal e reserva_sinal_consistency
        testReserva.setSinalPago(true);
        testReserva.setPrioridade(Reserva.ReservaPrioridade.ALTA);
        testReserva.setValorSinal(new BigDecimal("150.00"));
        testReserva.setSinalPagoEm(java.time.Instant.now());
        testReserva.setPagamentoStatus(Reserva.PagamentoStatus.CONFIRMADO);
        reservaRepository.save(testReserva);

        String requestBody = "{\"forma\": \"PIX\", \"valor\": 600.00}";

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/registrar-pagamento", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should register presential payment on RASCUNHO (wizard pays before emission)")
    void testRegistrarPagamentoPresencial_RascunhoAllowed() throws Exception {
        testReserva.setStatus(ReservaStatus.RASCUNHO);
        reservaRepository.save(testReserva);

        String requestBody = "{\"forma\": \"CARTAO_CREDITO\", \"valor\": 450.00}";

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/registrar-pagamento", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagamentoStatus").value("CONFIRMADO"))
            .andExpect(jsonPath("$.status").value("RASCUNHO"));
    }

    // ========================================================================
    // PIX da cobrança do balcão (QR copia-e-cola + envio por e-mail)
    // ========================================================================

    @Test
    @DisplayName("GET /pix gera BR Code com a chave da loja e o valor cobrado")
    void testGerarPix_Success() throws Exception {
        jdbcTemplate.update(
            "UPDATE tenant SET pix_chave = 'pix@loja.com.br', cidade = 'Florianópolis' WHERE id = ?", TENANT_ID);

        mockMvc.perform(get("/v1/tenants/{tenantId}/reservas/{id}/pix", TENANT_ID, testReserva.getId())
                .param("valor", "600.00")
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.chave").value("pix@loja.com.br"))
            .andExpect(jsonPath("$.valor").value(600.00))
            .andExpect(jsonPath("$.copiaECola").value(org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.startsWith("000201"),
                org.hamcrest.Matchers.containsString("br.gov.bcb.pix"),
                org.hamcrest.Matchers.containsString("pix@loja.com.br"),
                org.hamcrest.Matchers.containsString("5406600.00"))));
    }

    @Test
    @DisplayName("GET /pix sem chave PIX configurada na loja → 400")
    void testGerarPix_SemChaveConfigurada() throws Exception {
        jdbcTemplate.update("UPDATE tenant SET pix_chave = NULL WHERE id = ?", TENANT_ID);

        mockMvc.perform(get("/v1/tenants/{tenantId}/reservas/{id}/pix", TENANT_ID, testReserva.getId())
                .param("valor", "600.00")
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /pix com valor zero → 400")
    void testGerarPix_ValorInvalido() throws Exception {
        jdbcTemplate.update("UPDATE tenant SET pix_chave = 'pix@loja.com.br' WHERE id = ?", TENANT_ID);

        mockMvc.perform(get("/v1/tenants/{tenantId}/reservas/{id}/pix", TENANT_ID, testReserva.getId())
                .param("valor", "0")
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /enviar-pix-email envia ao e-mail do cliente da reserva")
    void testEnviarPixEmail_Success() throws Exception {
        jdbcTemplate.update("UPDATE tenant SET pix_chave = 'pix@loja.com.br' WHERE id = ?", TENANT_ID);

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/enviar-pix-email", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\": 600.00}")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("carlos.teste@email.com"));
    }

    @Test
    @DisplayName("POST /enviar-pix-email com cliente sem e-mail → 400")
    void testEnviarPixEmail_ClienteSemEmail() throws Exception {
        jdbcTemplate.update("UPDATE tenant SET pix_chave = 'pix@loja.com.br' WHERE id = ?", TENANT_ID);
        testCliente.setEmail(null);
        clienteRepository.save(testCliente);

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/enviar-pix-email", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\": 600.00}")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /enviar-pix-email negado para MECANICO (403)")
    void testEnviarPixEmail_ForbiddenForMecanico() throws Exception {
        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/enviar-pix-email", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\": 600.00}")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MECANICO"))))
            .andExpect(status().isForbidden());
    }

    private void pagarReserva(java.math.BigDecimal valor) throws Exception {
        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/registrar-pagamento", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"forma\": \"PIX\", \"valor\": " + valor + "}")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("confirmar-sinal (fluxo remoto/portal) lança PAGAMENTO PIX no folio — estorno funciona")
    void testConfirmarSinal_LancaPagamentoNoFolio() throws Exception {
        // Pagamento validado pela fila de sinais (não pelo balcão)
        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/confirmar-sinal", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorSinal\": 700.00, \"tipo\": \"TOTAL\"}")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_FINANCEIRO"))))
            .andExpect(status().isOk());

        var lancamentos = reservaLancamentoRepository.findByReservaIdOrderByCreatedAtAsc(testReserva.getId());
        org.assertj.core.api.Assertions.assertThat(lancamentos).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(lancamentos.get(0).getTipo())
            .isEqualTo(com.jetski.locacoes.domain.ReservaLancamento.Tipo.PAGAMENTO);
        org.assertj.core.api.Assertions.assertThat(lancamentos.get(0).getForma())
            .isEqualTo(com.jetski.locacoes.domain.ReservaLancamento.Forma.PIX);

        // O estorno agora encontra o recebido (bug: reserva do portal com folio vazio)
        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/registrar-estorno", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"forma\": \"PIX\", \"valor\": 700.00, \"observacao\": \"cancelou por chuva\"}")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_GERENTE"))))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should register estorno of paid+cancelled reservation (FINANCEIRO)")
    void testRegistrarEstorno_Success() throws Exception {
        pagarReserva(new java.math.BigDecimal("500.00"));
        testReserva = reservaRepository.findById(testReserva.getId()).orElseThrow();
        testReserva.setStatus(ReservaStatus.CANCELADA);
        reservaRepository.save(testReserva);

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/registrar-estorno", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"forma\": \"PIX\", \"valor\": 300.00, \"observacao\": \"cancelou por chuva\"}")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_FINANCEIRO"))))
            .andExpect(status().isOk());

        var lancamentos = reservaLancamentoRepository.findByReservaIdOrderByCreatedAtAsc(testReserva.getId());
        org.assertj.core.api.Assertions.assertThat(lancamentos).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(lancamentos.get(1).getTipo())
            .isEqualTo(com.jetski.locacoes.domain.ReservaLancamento.Tipo.ESTORNO);
        org.assertj.core.api.Assertions.assertThat(lancamentos.get(1).getValor())
            .isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("Should reject estorno greater than net received (parcial + excedente)")
    void testRegistrarEstorno_ExcedeRecebido() throws Exception {
        pagarReserva(new java.math.BigDecimal("500.00"));

        // 1º estorno parcial ok
        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/registrar-estorno", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"forma\": \"DINHEIRO\", \"valor\": 400.00, \"observacao\": \"parcial\"}")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_GERENTE"))))
            .andExpect(status().isOk());

        // 2º excede o líquido restante (100) → 400
        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/registrar-estorno", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"forma\": \"DINHEIRO\", \"valor\": 150.00, \"observacao\": \"excede\"}")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_GERENTE"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject estorno when no confirmed payment")
    void testRegistrarEstorno_SemPagamento() throws Exception {
        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/registrar-estorno", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"forma\": \"PIX\", \"valor\": 100.00, \"observacao\": \"nada pago\"}")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_GERENTE"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject estorno for OPERADOR (role sensível)")
    void testRegistrarEstorno_ForbiddenForOperador() throws Exception {
        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/registrar-estorno", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"forma\": \"PIX\", \"valor\": 100.00, \"observacao\": \"x\"}")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return reservation folio extrato with saldo summary")
    void testExtratoReserva() throws Exception {
        pagarReserva(new java.math.BigDecimal("450.00"));

        mockMvc.perform(get("/v1/tenants/{tenantId}/reservas/{id}/extrato", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lancamentos", hasSize(1)))
            .andExpect(jsonPath("$.lancamentos[0].tipo").value("PAGAMENTO"))
            .andExpect(jsonPath("$.totalPagamentos").value(450.00))
            .andExpect(jsonPath("$.saldo").value(-450.00));
    }

    @Test
    @DisplayName("Should mark NO_SHOW for confirmed reservation with past start time")
    void testMarcarNoShow_Success() throws Exception {
        testReserva.setDataInicio(LocalDateTime.now().minusHours(3));
        testReserva.setDataFimPrevista(LocalDateTime.now().minusHours(1));
        reservaRepository.save(testReserva);

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/no-show", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    @DisplayName("Should reject NO_SHOW when start time is in the future")
    void testMarcarNoShow_DataFutura() throws Exception {
        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/no-show", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject NO_SHOW for cancelled reservation")
    void testMarcarNoShow_Cancelada() throws Exception {
        testReserva.setDataInicio(LocalDateTime.now().minusHours(3));
        testReserva.setStatus(ReservaStatus.CANCELADA);
        reservaRepository.save(testReserva);

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/no-show", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should set pagamentoStatus=CONFIRMADO and tipo=SINAL on confirm (F2.1)")
    void testConfirmarSinal_SetsPagamentoStatus() throws Exception {
        String requestBody = "{\"valorSinal\": 100.00}";

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/confirmar-sinal", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagamentoStatus").value("CONFIRMADO"))
            .andExpect(jsonPath("$.pagamentoTipo").value("SINAL"));
    }

    @Test
    @DisplayName("Should confirm payment as TOTAL and set valorTotal (F2.1)")
    void testConfirmarPagamento_Total() throws Exception {
        String requestBody = "{\"valorSinal\": 900.00, \"tipo\": \"TOTAL\"}";

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/confirmar-sinal", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagamentoTipo").value("TOTAL"))
            .andExpect(jsonPath("$.valorTotal").value(900.00))
            .andExpect(jsonPath("$.prioridade").value("ALTA"));
    }

    @Test
    @DisplayName("Should reject payment with motivo (recusar-pagamento, F2.1)")
    void testRecusarPagamento_Success() throws Exception {
        String requestBody = "{\"motivo\": \"Comprovante ilegível\"}";

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/recusar-pagamento", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_FINANCEIRO"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagamentoStatus").value("RECUSADO"))
            .andExpect(jsonPath("$.sinalPago").value(false));
    }

    @Test
    @DisplayName("Should reject recusar-pagamento for MECANICO (403, F2.1)")
    void testRecusarPagamento_ForbiddenForMecanico() throws Exception {
        String requestBody = "{\"motivo\": \"x\"}";

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/recusar-pagamento", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MECANICO"))))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should reject confirmar-sinal when already paid")
    void testConfirmarSinal_AlreadyPaid() throws Exception {
        // Mark deposit as already paid (must also set ALTA priority and timestamp due to DB constraints)
        testReserva.setSinalPago(true);
        testReserva.setValorSinal(new BigDecimal("100.00"));
        testReserva.setSinalPagoEm(java.time.Instant.now());
        testReserva.setPrioridade(Reserva.ReservaPrioridade.ALTA);
        reservaRepository.save(testReserva);

        String requestBody = "{\"valorSinal\": 150.00}";

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/confirmar-sinal", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should allocate specific jetski to reservation")
    void testAlocarJetski_Success() throws Exception {
        // First confirm the reservation
        testReserva.setStatus(ReservaStatus.CONFIRMADA);
        testReserva.setJetskiId(null); // No jetski allocated yet
        reservaRepository.save(testReserva);

        String requestBody = String.format("{\"jetskiId\": \"%s\"}", testJetski.getId());

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/alocar-jetski", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jetskiId").value(testJetski.getId().toString()));
    }

    @Test
    @DisplayName("Should reject alocar-jetski when jetski already allocated")
    void testAlocarJetski_AlreadyAllocated() throws Exception {
        // Reservation already has jetski
        testReserva.setStatus(ReservaStatus.CONFIRMADA);
        testReserva.setJetskiId(testJetski.getId());
        reservaRepository.save(testReserva);

        UUID otherJetskiId = UUID.randomUUID();
        String requestBody = String.format("{\"jetskiId\": \"%s\"}", otherJetskiId);

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/alocar-jetski", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should check modelo availability for period")
    void testCheckDisponibilidade_Success() throws Exception {
        LocalDateTime dataInicio = LocalDateTime.now().plusDays(5);
        LocalDateTime dataFim = dataInicio.plusHours(2);

        mockMvc.perform(get("/v1/tenants/{tenantId}/reservas/disponibilidade", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .param("modeloId", testModelo.getId().toString())
                .param("dataInicio", dataInicio.toString())
                .param("dataFimPrevista", dataFim.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalJetskis").exists())
            .andExpect(jsonPath("$.aceitaComSinal").exists())
            .andExpect(jsonPath("$.aceitaSemSinal").exists());
    }

    @Test
    @DisplayName("Should create reservation without jetskiId (modelo-based)")
    void testCreateReserva_ModeloBased_NoJetski() throws Exception {
        LocalDateTime dataInicio = LocalDateTime.now().plusDays(10);
        LocalDateTime dataFim = dataInicio.plusHours(2);

        ReservaCreateRequest request = ReservaCreateRequest.builder()
                .modeloId(testModelo.getId())
                .jetskiId(null)  // No specific jetski
                .clienteId(testCliente.getId())
                .dataInicio(dataInicio)
                .dataFimPrevista(dataFim)
                .observacoes("Modelo-based booking test")
                .build();

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.modeloId").value(testModelo.getId().toString()))
            .andExpect(jsonPath("$.jetskiId").doesNotExist())
            .andExpect(jsonPath("$.prioridade").value("BAIXA"));
    }

    @Test
    @DisplayName("Should reject alocar-jetski when jetski belongs to different modelo")
    void testAlocarJetski_DifferentModelo() throws Exception {
        // Create a jetski from a different modelo
        Modelo otherModelo = Modelo.builder()
                .tenantId(TENANT_ID)
                .nome("Kawasaki Ultra 310")
                .fabricante("Kawasaki")
                .precoBaseHora(new BigDecimal("400.00"))
                .ativo(true)
                .build();
        modeloRepository.save(otherModelo);

        Jetski otherJetski = Jetski.builder()
                .tenantId(TENANT_ID)
                .modeloId(otherModelo.getId())
                .serie("KW-2024-001")
                .status(JetskiStatus.DISPONIVEL)
                .ativo(true)
                .build();
        jetskiRepository.save(otherJetski);

        testReserva.setStatus(ReservaStatus.CONFIRMADA);
        testReserva.setJetskiId(null);
        reservaRepository.save(testReserva);

        String requestBody = String.format("{\"jetskiId\": \"%s\"}", otherJetski.getId());

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/alocar-jetski", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject alocar-jetski when reservation not confirmed")
    void testAlocarJetski_NotConfirmed() throws Exception {
        testReserva.setStatus(ReservaStatus.PENDENTE);
        testReserva.setJetskiId(null);
        reservaRepository.save(testReserva);

        String requestBody = String.format("{\"jetskiId\": \"%s\"}", testJetski.getId());

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/alocar-jetski", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should confirm reservation without jetski allocated")
    void testConfirmReserva_NoJetskiAllocated() throws Exception {
        testReserva.setJetskiId(null);  // No jetski allocated
        testReserva.setStatus(ReservaStatus.PENDENTE);
        reservaRepository.save(testReserva);

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/confirmar", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMADA"))
            .andExpect(jsonPath("$.jetskiId").doesNotExist());
    }

    @Test
    @DisplayName("Should reject alocar-jetski when jetski not DISPONIVEL")
    void testAlocarJetski_JetskiNotAvailable() throws Exception {
        testReserva.setStatus(ReservaStatus.CONFIRMADA);
        testReserva.setJetskiId(null);
        reservaRepository.save(testReserva);

        // Set jetski to MANUTENCAO status
        testJetski.setStatus(JetskiStatus.MANUTENCAO);
        jetskiRepository.save(testJetski);

        String requestBody = String.format("{\"jetskiId\": \"%s\"}", testJetski.getId());

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/alocar-jetski", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject confirmar-sinal with invalid amount")
    void testConfirmarSinal_InvalidAmount() throws Exception {
        String requestBody = "{\"valorSinal\": -50.00}";

        mockMvc.perform(post("/v1/tenants/{tenantId}/reservas/{id}/confirmar-sinal", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_TENANT"))))
            .andExpect(status().isBadRequest());
    }
}
