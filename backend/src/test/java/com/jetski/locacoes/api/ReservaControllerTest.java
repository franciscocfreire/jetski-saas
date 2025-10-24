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
        jdbcTemplate.execute("DELETE FROM commission_policy WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].status").value("PENDENTE"));
    }

    @Test
    @DisplayName("Should get reservation by ID")
    void testGetReserva_ById() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/reservas/{id}", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(pendingReserva.getId().toString()))
            .andExpect(jsonPath("$.status").value("CONFIRMADA"));
    }

    @Test
    @DisplayName("Should cancel reservation")
    void testCancelReserva() throws Exception {
        mockMvc.perform(delete("/v1/tenants/{tenantId}/reservas/{id}", TENANT_ID, testReserva.getId())
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sinalPago").value(true))
            .andExpect(jsonPath("$.prioridade").value("ALTA"))
            .andExpect(jsonPath("$.valorSinal").value(150.00));
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
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
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isBadRequest());
    }
}
