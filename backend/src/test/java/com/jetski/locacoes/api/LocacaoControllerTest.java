package com.jetski.locacoes.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.locacoes.api.dto.CheckInFromReservaRequest;
import com.jetski.locacoes.api.dto.CheckInWalkInRequest;
import com.jetski.locacoes.api.dto.CheckOutRequest;
import com.jetski.locacoes.domain.*;
import com.jetski.locacoes.internal.repository.*;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
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

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for LocacaoController
 *
 * Tests rental operations (check-in and check-out):
 * - Check-in from reservation
 * - Check-in walk-in
 * - Check-out with RN01 calculation
 * - List rentals (all, by status, by jetski, by cliente)
 * - Get rental by ID
 * - Validation rules
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@AutoConfigureMockMvc
@DisplayName("LocacaoController Tests")
class LocacaoControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocacaoRepository locacaoRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private JetskiRepository jetskiRepository;

    @Autowired
    private ModeloRepository modeloRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private VendedorRepository vendedorRepository;

    @Autowired
    private FotoRepository fotoRepository;

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
    private Vendedor testVendedor;
    private Reserva testReserva;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);

        // Clean up in correct FK dependency order (most dependent first)
        jdbcTemplate.execute("DELETE FROM foto WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM abastecimento WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        // Break circular FK: reserva ↔ locacao
        jdbcTemplate.execute("UPDATE reserva SET locacao_id = NULL WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM locacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM os_manutencao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM jetski WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM commission_policy WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM fuel_policy WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM modelo WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM cliente WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM vendedor WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");

        // Ensure test user has identity provider mapping (needed for authentication filter)
        try {
            jdbcTemplate.execute("INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id, linked_at) " +
                                 "VALUES ('11111111-1111-1111-1111-111111111111', 'keycloak', '11111111-1111-1111-1111-111111111111', NOW())");
        } catch (Exception ignored) {
            // Ignore if already exists
        }

        // Create test data
        testModelo = Modelo.builder()
            .tenantId(TENANT_ID)
            .fabricante("Yamaha")
            .nome("VX Cruiser")
            .capacidadePessoas(3)
            .precoBaseHora(new BigDecimal("150.00"))
            .toleranciaMin(5)
            .build();
        testModelo = modeloRepository.save(testModelo);

        testJetski = Jetski.builder()
            .tenantId(TENANT_ID)
            .modeloId(testModelo.getId())
            .serie("YAM-001")
            .horimetroAtual(new BigDecimal("100.0"))
            .status(JetskiStatus.DISPONIVEL)
            .build();
        testJetski = jetskiRepository.save(testJetski);

        testCliente = Cliente.builder()
            .tenantId(TENANT_ID)
            .nome("João Silva")
            .email("joao.silva@example.com")
            .telefone("11987654321")
            .build();
        testCliente = clienteRepository.save(testCliente);

        testVendedor = Vendedor.builder()
            .tenantId(TENANT_ID)
            .nome("Maria Vendedora")
            .build();
        testVendedor = vendedorRepository.save(testVendedor);

        // Create default GLOBAL fuel policy (RN03: INCLUSO mode - no charge)
        jdbcTemplate.execute(
            "INSERT INTO fuel_policy (tenant_id, nome, tipo, aplicavel_a, valor_taxa_por_hora, " +
            "comissionavel, ativo, prioridade, created_at, updated_at) " +
            "VALUES ('" + TENANT_ID + "', 'Global - Combustível Incluso', 'INCLUSO', 'GLOBAL', " +
            "null, false, true, 0, NOW(), NOW())"
        );

        // Mock OPA to allow all
        when(opaAuthorizationService.authorize(any()))
            .thenReturn(OPADecision.builder().allow(true).build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ===================================================================
    // Check-in from Reservation Tests
    // ===================================================================

    @Test
    @DisplayName("POST /check-in/reserva - Success: Convert reservation to rental")
    void testCheckInFromReservation_Success() throws Exception {
        // Given: A confirmed reservation
        testReserva = Reserva.builder()
            .tenantId(TENANT_ID)
            .modeloId(testModelo.getId())
            .jetskiId(testJetski.getId())
            .clienteId(testCliente.getId())
            .vendedorId(testVendedor.getId())
            .dataInicio(LocalDateTime.now())
            .dataFimPrevista(LocalDateTime.now().plusHours(2))
            .status(Reserva.ReservaStatus.CONFIRMADA)
            .build();
        testReserva = reservaRepository.save(testReserva);

        CheckInFromReservaRequest request = CheckInFromReservaRequest.builder()
            .reservaId(testReserva.getId())
            .horimetroInicio(new BigDecimal("100.0"))
            .observacoes("Check-in normal")
            .build();

        // When: Check-in from reservation
        mockMvc.perform(post("/v1/tenants/{tenantId}/locacoes/check-in/reserva", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Returns 201 Created with rental details
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
            .andExpect(jsonPath("$.reservaId").value(testReserva.getId().toString()))
            .andExpect(jsonPath("$.jetskiId").value(testJetski.getId().toString()))
            .andExpect(jsonPath("$.clienteId").value(testCliente.getId().toString()))
            .andExpect(jsonPath("$.vendedorId").value(testVendedor.getId().toString()))
            .andExpect(jsonPath("$.horimetroInicio").value(100.0))
            .andExpect(jsonPath("$.status").value("EM_CURSO"))
            .andExpect(jsonPath("$.dataCheckIn").exists())
            .andExpect(jsonPath("$.dataCheckOut").doesNotExist());

        // Verify jetski status changed to LOCADO
        Jetski updatedJetski = jetskiRepository.findById(testJetski.getId()).orElseThrow();
        assert updatedJetski.getStatus() == JetskiStatus.LOCADO;

        // Verify reservation status changed to FINALIZADA
        Reserva updatedReserva = reservaRepository.findById(testReserva.getId()).orElseThrow();
        assert updatedReserva.getStatus() == Reserva.ReservaStatus.FINALIZADA;
        assert updatedReserva.getLocacaoId() != null;
    }

    @Test
    @DisplayName("POST /check-in/reserva - Fail: Reservation not CONFIRMADA")
    void testCheckInFromReservation_Fail_NotConfirmed() throws Exception {
        // Given: A PENDENTE reservation
        testReserva = Reserva.builder()
            .tenantId(TENANT_ID)
            .modeloId(testModelo.getId())
            .jetskiId(testJetski.getId())
            .clienteId(testCliente.getId())
            .dataInicio(LocalDateTime.now())
            .dataFimPrevista(LocalDateTime.now().plusHours(2))
            .status(Reserva.ReservaStatus.PENDENTE)
            .build();
        testReserva = reservaRepository.save(testReserva);

        CheckInFromReservaRequest request = CheckInFromReservaRequest.builder()
            .reservaId(testReserva.getId())
            .horimetroInicio(new BigDecimal("100.0"))
            .build();

        // When/Then: Check-in fails with 400 Bad Request
        mockMvc.perform(post("/v1/tenants/{tenantId}/locacoes/check-in/reserva", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("CONFIRMADA")));
    }

    @Test
    @DisplayName("POST /check-in/reserva - Fail: Jetski not DISPONIVEL")
    void testCheckInFromReservation_Fail_JetskiNotAvailable() throws Exception {
        // Given: Jetski in MANUTENCAO
        testJetski.setStatus(JetskiStatus.MANUTENCAO);
        jetskiRepository.save(testJetski);

        testReserva = Reserva.builder()
            .tenantId(TENANT_ID)
            .modeloId(testModelo.getId())
            .jetskiId(testJetski.getId())
            .clienteId(testCliente.getId())
            .dataInicio(LocalDateTime.now())
            .dataFimPrevista(LocalDateTime.now().plusHours(2))
            .status(Reserva.ReservaStatus.CONFIRMADA)
            .build();
        testReserva = reservaRepository.save(testReserva);

        CheckInFromReservaRequest request = CheckInFromReservaRequest.builder()
            .reservaId(testReserva.getId())
            .horimetroInicio(new BigDecimal("100.0"))
            .build();

        // When/Then: Check-in fails with 400 Bad Request
        mockMvc.perform(post("/v1/tenants/{tenantId}/locacoes/check-in/reserva", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("disponível")));
    }

    @Test
    @DisplayName("POST /check-in/reserva - Fail: Reservation has no jetski allocated")
    void testCheckInFromReservation_Fail_NoJetskiAllocated() throws Exception {
        // Given: Reservation with NO jetski_id
        testReserva = Reserva.builder()
            .tenantId(TENANT_ID)
            .modeloId(testModelo.getId())
            .jetskiId(null)  // NO jetski allocated
            .clienteId(testCliente.getId())
            .dataInicio(LocalDateTime.now())
            .dataFimPrevista(LocalDateTime.now().plusHours(2))
            .status(Reserva.ReservaStatus.CONFIRMADA)
            .build();
        testReserva = reservaRepository.save(testReserva);

        CheckInFromReservaRequest request = CheckInFromReservaRequest.builder()
            .reservaId(testReserva.getId())
            .horimetroInicio(new BigDecimal("100.0"))
            .build();

        // When/Then: Check-in fails with 400 Bad Request
        mockMvc.perform(post("/v1/tenants/{tenantId}/locacoes/check-in/reserva", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("jetski alocado")));
    }

    // ===================================================================
    // Walk-in Check-in Tests
    // ===================================================================

    @Test
    @DisplayName("POST /check-in/walk-in - Success: Create rental without reservation")
    void testCheckInWalkIn_Success() throws Exception {
        // Given: Walk-in request
        CheckInWalkInRequest request = CheckInWalkInRequest.builder()
            .jetskiId(testJetski.getId())
            .clienteId(testCliente.getId())
            .vendedorId(testVendedor.getId())
            .horimetroInicio(new BigDecimal("100.0"))
            .duracaoPrevista(60)
            .observacoes("Walk-in customer")
            .build();

        // When: Walk-in check-in
        mockMvc.perform(post("/v1/tenants/{tenantId}/locacoes/check-in/walk-in", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Returns 201 Created with rental details
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.reservaId").doesNotExist())
            .andExpect(jsonPath("$.jetskiId").value(testJetski.getId().toString()))
            .andExpect(jsonPath("$.clienteId").value(testCliente.getId().toString()))
            .andExpect(jsonPath("$.duracaoPrevista").value(60))
            .andExpect(jsonPath("$.status").value("EM_CURSO"));

        // Verify jetski status changed to LOCADO
        Jetski updatedJetski = jetskiRepository.findById(testJetski.getId()).orElseThrow();
        assert updatedJetski.getStatus() == JetskiStatus.LOCADO;
    }

    @Test
    @DisplayName("POST /check-in/walk-in - Fail: Jetski already in use")
    void testCheckInWalkIn_Fail_JetskiAlreadyInUse() throws Exception {
        // Given: Existing active rental
        Locacao existingLocacao = Locacao.builder()
            .tenantId(TENANT_ID)
            .jetskiId(testJetski.getId())
            .clienteId(testCliente.getId())
            .dataCheckIn(LocalDateTime.now())
            .horimetroInicio(new BigDecimal("100.0"))
            .duracaoPrevista(60)
            .status(LocacaoStatus.EM_CURSO)
            .build();
        locacaoRepository.save(existingLocacao);

        CheckInWalkInRequest request = CheckInWalkInRequest.builder()
            .jetskiId(testJetski.getId())
            .clienteId(testCliente.getId())
            .horimetroInicio(new BigDecimal("100.0"))
            .duracaoPrevista(60)
            .build();

        // When/Then: Check-in fails with 400 Bad Request
        mockMvc.perform(post("/v1/tenants/{tenantId}/locacoes/check-in/walk-in", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("locação ativa")));
    }

    // ===================================================================
    // Check-out Tests with RN01 Calculation
    // ===================================================================

    @Test
    @DisplayName("POST /{id}/check-out - Success: Calculate billable time with RN01")
    void testCheckOut_Success_RN01Calculation() throws Exception {
        // Given: Active rental
        Locacao locacao = Locacao.builder()
            .tenantId(TENANT_ID)
            .jetskiId(testJetski.getId())
            .clienteId(testCliente.getId())
            .dataCheckIn(LocalDateTime.now().minusHours(1))
            .horimetroInicio(new BigDecimal("100.0"))
            .duracaoPrevista(60)
            .status(LocacaoStatus.EM_CURSO)
            .build();
        locacao = locacaoRepository.save(locacao);

        // Create 4 mandatory check-out photos (Sprint 2 requirement)
        createCheckOutPhotos(locacao.getId());

        testJetski.setStatus(JetskiStatus.LOCADO);
        jetskiRepository.save(testJetski);

        CheckOutRequest request = CheckOutRequest.builder()
            .horimetroFim(new BigDecimal("101.5"))  // 1.5 hours = 90 minutes used
            .observacoes("Check-out normal")
            .checklistEntradaJson("[\"motor_ok\",\"casco_ok\",\"limpeza_ok\"]")  // RN05: mandatory checklist
            .build();

        // When: Check-out
        mockMvc.perform(post("/v1/tenants/{tenantId}/locacoes/{id}/check-out", TENANT_ID, locacao.getId())
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Returns 200 OK with calculated values
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(locacao.getId().toString()))
            .andExpect(jsonPath("$.horimetroFim").value(101.5))
            .andExpect(jsonPath("$.minutosUsados").value(90))
            // RN01: 90-5=85min → ceil(85/15)*15 = 90min billable
            .andExpect(jsonPath("$.minutosFaturaveis").value(90))
            // 90min at R$150/hour = R$225.00
            .andExpect(jsonPath("$.valorBase").value(225.00))
            .andExpect(jsonPath("$.valorTotal").value(225.00))
            .andExpect(jsonPath("$.status").value("FINALIZADA"))
            .andExpect(jsonPath("$.dataCheckOut").exists());

        // Verify jetski status changed to DISPONIVEL
        Jetski updatedJetski = jetskiRepository.findById(testJetski.getId()).orElseThrow();
        assert updatedJetski.getStatus() == JetskiStatus.DISPONIVEL;
    }

    @Test
    @DisplayName("POST /{id}/check-out - RN01 Scenario 1.1: 10min used, 5min tolerance → 15min billable")
    void testCheckOut_RN01_Scenario1_1() throws Exception {
        // Given: Active rental
        Locacao locacao = Locacao.builder()
            .tenantId(TENANT_ID)
            .jetskiId(testJetski.getId())
            .clienteId(testCliente.getId())
            .dataCheckIn(LocalDateTime.now())
            .horimetroInicio(new BigDecimal("100.0"))
            .duracaoPrevista(30)
            .status(LocacaoStatus.EM_CURSO)
            .build();
        locacao = locacaoRepository.save(locacao);

        // Create 4 mandatory check-out photos (Sprint 2 requirement)
        createCheckOutPhotos(locacao.getId());

        testJetski.setStatus(JetskiStatus.LOCADO);
        jetskiRepository.save(testJetski);

        CheckOutRequest request = CheckOutRequest.builder()
            .horimetroFim(new BigDecimal("100.166667"))  // ~10 minutes (0.166667 hours)
            .checklistEntradaJson("[\"motor_ok\"]")  // RN05: mandatory checklist
            .build();

        // When: Check-out
        mockMvc.perform(post("/v1/tenants/{tenantId}/locacoes/{id}/check-out", TENANT_ID, locacao.getId())
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: RN01 applied correctly
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.minutosUsados").value(10))
            .andExpect(jsonPath("$.minutosFaturaveis").value(15))  // 10-5=5 → round to 15
            .andExpect(jsonPath("$.valorBase").value(37.50));  // 15min at R$150/hour
    }

    @Test
    @DisplayName("POST /{id}/check-out - RN01 Scenario 1.3: Within tolerance → 0 billable")
    void testCheckOut_RN01_Scenario1_3_WithinTolerance() throws Exception {
        // Given: Active rental
        Locacao locacao = Locacao.builder()
            .tenantId(TENANT_ID)
            .jetskiId(testJetski.getId())
            .clienteId(testCliente.getId())
            .dataCheckIn(LocalDateTime.now())
            .horimetroInicio(new BigDecimal("100.0"))
            .duracaoPrevista(30)
            .status(LocacaoStatus.EM_CURSO)
            .build();
        locacao = locacaoRepository.save(locacao);

        // Create 4 mandatory check-out photos (Sprint 2 requirement)
        createCheckOutPhotos(locacao.getId());

        testJetski.setStatus(JetskiStatus.LOCADO);
        jetskiRepository.save(testJetski);

        CheckOutRequest request = CheckOutRequest.builder()
            .horimetroFim(new BigDecimal("100.083333"))  // 5 minutes (0.083333 hours)
            .checklistEntradaJson("[\"motor_ok\"]")  // RN05: mandatory checklist
            .build();

        // When: Check-out
        mockMvc.perform(post("/v1/tenants/{tenantId}/locacoes/{id}/check-out", TENANT_ID, locacao.getId())
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Within tolerance, 0 billable
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.minutosUsados").value(5))
            .andExpect(jsonPath("$.minutosFaturaveis").value(0))
            .andExpect(jsonPath("$.valorBase").value(0.00));
    }

    @Test
    @DisplayName("POST /{id}/check-out - Fail: Locacao not EM_CURSO")
    void testCheckOut_Fail_NotEmCurso() throws Exception {
        // Given: Already completed rental
        Locacao locacao = Locacao.builder()
            .tenantId(TENANT_ID)
            .jetskiId(testJetski.getId())
            .clienteId(testCliente.getId())
            .dataCheckIn(LocalDateTime.now().minusHours(2))
            .dataCheckOut(LocalDateTime.now().minusHours(1))
            .horimetroInicio(new BigDecimal("100.0"))
            .horimetroFim(new BigDecimal("101.0"))
            .duracaoPrevista(60)
            .status(LocacaoStatus.FINALIZADA)
            .build();
        locacao = locacaoRepository.save(locacao);

        CheckOutRequest request = CheckOutRequest.builder()
            .horimetroFim(new BigDecimal("102.0"))
            .checklistEntradaJson("[\"motor_ok\"]")  // RN05: mandatory checklist
            .build();

        // When/Then: Check-out fails
        mockMvc.perform(post("/v1/tenants/{tenantId}/locacoes/{id}/check-out", TENANT_ID, locacao.getId())
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("EM_CURSO")));
    }

    @Test
    @DisplayName("POST /{id}/check-out - Fail: Horimetro fim < inicio")
    void testCheckOut_Fail_InvalidHorimetro() throws Exception {
        // Given: Active rental
        Locacao locacao = Locacao.builder()
            .tenantId(TENANT_ID)
            .jetskiId(testJetski.getId())
            .clienteId(testCliente.getId())
            .dataCheckIn(LocalDateTime.now())
            .horimetroInicio(new BigDecimal("100.0"))
            .duracaoPrevista(60)
            .status(LocacaoStatus.EM_CURSO)
            .build();
        locacao = locacaoRepository.save(locacao);

        CheckOutRequest request = CheckOutRequest.builder()
            .horimetroFim(new BigDecimal("99.0"))  // Less than inicio
            .checklistEntradaJson("[\"motor_ok\"]")  // RN05: mandatory checklist
            .build();

        // When/Then: Check-out fails
        mockMvc.perform(post("/v1/tenants/{tenantId}/locacoes/{id}/check-out", TENANT_ID, locacao.getId())
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("Horímetro")));
    }

    @Test
    @DisplayName("POST /{id}/check-out - Fail: Missing 4 mandatory photos")
    void testCheckOut_Fail_MissingPhotos() throws Exception {
        // Given: Active rental WITHOUT 4 mandatory photos
        Locacao locacao = Locacao.builder()
            .tenantId(TENANT_ID)
            .jetskiId(testJetski.getId())
            .clienteId(testCliente.getId())
            .dataCheckIn(LocalDateTime.now())
            .horimetroInicio(new BigDecimal("100.0"))
            .duracaoPrevista(60)
            .status(LocacaoStatus.EM_CURSO)
            .build();
        locacao = locacaoRepository.save(locacao);

        // NO photos created - should fail validation

        testJetski.setStatus(JetskiStatus.LOCADO);
        jetskiRepository.save(testJetski);

        CheckOutRequest request = CheckOutRequest.builder()
            .horimetroFim(new BigDecimal("101.0"))
            .checklistEntradaJson("[\"motor_ok\"]")  // RN05: mandatory checklist
            .build();

        // When/Then: Check-out fails with 400 due to missing photos
        mockMvc.perform(post("/v1/tenants/{tenantId}/locacoes/{id}/check-out", TENANT_ID, locacao.getId())
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("4 fotos obrigatórias")));
    }

    // ===================================================================
    // List and Get Tests
    // ===================================================================

    @Test
    @DisplayName("GET /{id} - Success: Get rental by ID")
    void testGetById_Success() throws Exception {
        // Given: Completed rental
        Locacao locacao = Locacao.builder()
            .tenantId(TENANT_ID)
            .jetskiId(testJetski.getId())
            .clienteId(testCliente.getId())
            .dataCheckIn(LocalDateTime.now().minusHours(2))
            .dataCheckOut(LocalDateTime.now().minusHours(1))
            .horimetroInicio(new BigDecimal("100.0"))
            .horimetroFim(new BigDecimal("101.0"))
            .duracaoPrevista(60)
            .minutosUsados(60)
            .minutosFaturaveis(60)
            .valorBase(new BigDecimal("150.00"))
            .valorTotal(new BigDecimal("150.00"))
            .status(LocacaoStatus.FINALIZADA)
            .build();
        locacao = locacaoRepository.save(locacao);

        // When: Get by ID
        mockMvc.perform(get("/v1/tenants/{tenantId}/locacoes/{id}", TENANT_ID, locacao.getId())
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            // Then: Returns rental details
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(locacao.getId().toString()))
            .andExpect(jsonPath("$.status").value("FINALIZADA"))
            .andExpect(jsonPath("$.valorTotal").value(150.00));
    }

    @Test
    @DisplayName("GET / - Success: List all rentals")
    void testList_Success() throws Exception {
        // Given: Multiple rentals
        Locacao locacao1 = createTestLocacao(LocacaoStatus.EM_CURSO);
        Locacao locacao2 = createTestLocacao(LocacaoStatus.FINALIZADA);

        // When: List all
        mockMvc.perform(get("/v1/tenants/{tenantId}/locacoes", TENANT_ID)
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            // Then: Returns all rentals
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("GET /?status=EM_CURSO - Success: Filter by status")
    void testList_FilterByStatus() throws Exception {
        // Given: Rentals with different statuses
        createTestLocacao(LocacaoStatus.EM_CURSO);
        createTestLocacao(LocacaoStatus.FINALIZADA);

        // When: Filter by EM_CURSO
        mockMvc.perform(get("/v1/tenants/{tenantId}/locacoes", TENANT_ID)
                .param("status", "EM_CURSO")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            // Then: Returns only EM_CURSO rentals
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].status").value("EM_CURSO"));
    }

    @Test
    @DisplayName("GET /?jetskiId=xxx - Success: Filter by jetski")
    void testList_FilterByJetski() throws Exception {
        // Given: Rentals for different jetskis
        Jetski otherJetski = Jetski.builder()
            .tenantId(TENANT_ID)
            .modeloId(testModelo.getId())
            .serie("YAM-002")
            .horimetroAtual(new BigDecimal("50.0"))
            .status(JetskiStatus.DISPONIVEL)
            .build();
        otherJetski = jetskiRepository.save(otherJetski);

        createTestLocacao(testJetski.getId(), LocacaoStatus.FINALIZADA);
        createTestLocacao(otherJetski.getId(), LocacaoStatus.FINALIZADA);

        // When: Filter by specific jetski
        mockMvc.perform(get("/v1/tenants/{tenantId}/locacoes", TENANT_ID)
                .param("jetskiId", testJetski.getId().toString())
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            // Then: Returns only rentals for that jetski
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].jetskiId").value(testJetski.getId().toString()));
    }

    @Test
    @DisplayName("GET /?clienteId=xxx - Success: Filter by cliente")
    void testList_FilterByCliente() throws Exception {
        // Given: Another cliente
        Cliente otherCliente = Cliente.builder()
            .tenantId(TENANT_ID)
            .nome("Maria Santos")
            .email("maria.santos@example.com")
            .telefone("11987654322")
            .build();
        otherCliente = clienteRepository.save(otherCliente);

        // Create rentals for different clientes
        Locacao locacao1 = Locacao.builder()
            .tenantId(TENANT_ID)
            .jetskiId(testJetski.getId())
            .clienteId(testCliente.getId())
            .dataCheckIn(LocalDateTime.now().minusHours(2))
            .horimetroInicio(new BigDecimal("100.0"))
            .duracaoPrevista(60)
            .status(LocacaoStatus.FINALIZADA)
            .build();
        locacaoRepository.save(locacao1);

        Locacao locacao2 = Locacao.builder()
            .tenantId(TENANT_ID)
            .jetskiId(testJetski.getId())
            .clienteId(otherCliente.getId())
            .dataCheckIn(LocalDateTime.now().minusHours(1))
            .horimetroInicio(new BigDecimal("100.0"))
            .duracaoPrevista(60)
            .status(LocacaoStatus.FINALIZADA)
            .build();
        locacaoRepository.save(locacao2);

        // When: Filter by specific cliente
        mockMvc.perform(get("/v1/tenants/{tenantId}/locacoes", TENANT_ID)
                .param("clienteId", testCliente.getId().toString())
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_ID.toString())
                                         .claim("sub", USER_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            // Then: Returns only rentals for that cliente
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].clienteId").value(testCliente.getId().toString()));
    }

    // ===================================================================
    // Helper Methods
    // ===================================================================

    private Locacao createTestLocacao(LocacaoStatus status) {
        return createTestLocacao(testJetski.getId(), status);
    }

    private Locacao createTestLocacao(UUID jetskiId, LocacaoStatus status) {
        Locacao locacao = Locacao.builder()
            .tenantId(TENANT_ID)
            .jetskiId(jetskiId)
            .clienteId(testCliente.getId())
            .dataCheckIn(LocalDateTime.now().minusHours(2))
            .horimetroInicio(new BigDecimal("100.0"))
            .duracaoPrevista(60)
            .status(status)
            .build();

        if (status == LocacaoStatus.FINALIZADA) {
            locacao.setDataCheckOut(LocalDateTime.now().minusHours(1));
            locacao.setHorimetroFim(new BigDecimal("101.0"));
            locacao.setMinutosUsados(60);
            locacao.setMinutosFaturaveis(60);
            locacao.setValorBase(new BigDecimal("150.00"));
            locacao.setValorTotal(new BigDecimal("150.00"));
        }

        return locacaoRepository.save(locacao);
    }

    /**
     * Helper method to create the 4 mandatory check-in photos for a locacao
     */
    private void createCheckInPhotos(UUID locacaoId) {
        createPhoto(locacaoId, FotoTipo.CHECKIN_FRENTE);
        createPhoto(locacaoId, FotoTipo.CHECKIN_LATERAL_ESQ);
        createPhoto(locacaoId, FotoTipo.CHECKIN_LATERAL_DIR);
        createPhoto(locacaoId, FotoTipo.CHECKIN_HORIMETRO);
    }

    /**
     * Helper method to create the 4 mandatory check-out photos for a locacao
     */
    private void createCheckOutPhotos(UUID locacaoId) {
        createPhoto(locacaoId, FotoTipo.CHECKOUT_FRENTE);
        createPhoto(locacaoId, FotoTipo.CHECKOUT_LATERAL_ESQ);
        createPhoto(locacaoId, FotoTipo.CHECKOUT_LATERAL_DIR);
        createPhoto(locacaoId, FotoTipo.CHECKOUT_HORIMETRO);
    }

    /**
     * Helper method to create a single photo
     */
    private void createPhoto(UUID locacaoId, FotoTipo tipo) {
        Foto foto = Foto.builder()
            .tenantId(TENANT_ID)
            .locacaoId(locacaoId)
            .jetskiId(testJetski.getId())
            .tipo(tipo)
            .url("https://fake-storage.local/foto.jpg")
            .s3Key(TENANT_ID + "/" + locacaoId + "/" + tipo.name() + ".jpg")
            .filename(tipo.name() + ".jpg")
            .contentType("image/jpeg")
            .sizeBytes(1024L)
            .sha256Hash("abc123")
            .uploadedAt(java.time.Instant.now())
            .build();
        fotoRepository.save(foto);
    }
}
