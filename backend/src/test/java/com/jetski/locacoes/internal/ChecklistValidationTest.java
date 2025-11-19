package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.*;
import com.jetski.locacoes.internal.repository.FotoRepository;
import com.jetski.locacoes.internal.repository.LocacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RN05: Checklist + 4 mandatory photos validation
 *
 * Test scenarios:
 * 1. Check-out should fail if checklist is missing
 * 2. Check-out should fail if less than 4 photos
 * 3. Check-out should succeed with valid checklist + 4 photos
 * 4. Photo validation should check for all 4 required types
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class ChecklistValidationTest {

    @Mock
    private LocacaoRepository locacaoRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private JetskiService jetskiService;

    @Mock
    private ModeloService modeloService;

    @Mock
    private ClienteService clienteService;

    @Mock
    private LocacaoCalculatorService calculatorService;

    @Mock
    private PhotoValidationService photoValidationService;

    @Mock
    private com.jetski.combustivel.internal.FuelPolicyService fuelPolicyService;

    @InjectMocks
    private LocacaoService locacaoService;

    private UUID tenantId;
    private UUID locacaoId;
    private UUID jetskiId;
    private Locacao locacao;
    private Jetski jetski;
    private Modelo modelo;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        locacaoId = UUID.randomUUID();
        jetskiId = UUID.randomUUID();

        // Setup locacao in EM_CURSO status
        locacao = Locacao.builder()
            .id(locacaoId)
            .tenantId(tenantId)
            .jetskiId(jetskiId)
            .clienteId(UUID.randomUUID())
            .dataCheckIn(LocalDateTime.now().minusHours(2))
            .horimetroInicio(new BigDecimal("100.0"))
            .duracaoPrevista(120)
            .status(LocacaoStatus.EM_CURSO)
            .checklistSaidaJson("[\"motor_ok\",\"casco_ok\",\"gasolina_ok\"]")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        jetski = new Jetski();
        jetski.setId(jetskiId);
        jetski.setTenantId(tenantId);
        jetski.setStatus(JetskiStatus.LOCADO);
        jetski.setModeloId(UUID.randomUUID());

        modelo = new Modelo();
        modelo.setId(jetski.getModeloId());
        modelo.setToleranciaMin(5);
        modelo.setPrecoBaseHora(new BigDecimal("100.00"));

        // Mock fuel policy service to return zero cost (INCLUSO mode)
        // Lenient because not all tests use checkout (some test check-in only)
        lenient().when(fuelPolicyService.calcularCustoCombustivel(any(), any(UUID.class)))
            .thenReturn(BigDecimal.ZERO);
    }

    /**
     * RN05.1: Check-out deve falhar se checklist de entrada estiver ausente
     */
    @Test
    void testCheckOut_ShouldFailWhenChecklistMissing() {
        // Given: Locacao without checklist
        when(locacaoRepository.findByIdAndTenantId(locacaoId, tenantId))
            .thenReturn(Optional.of(locacao));
        when(jetskiService.findById(jetskiId)).thenReturn(jetski);
        when(modeloService.findById(jetski.getModeloId())).thenReturn(modelo);
        when(calculatorService.calculateUsedMinutes(any(BigDecimal.class), any(BigDecimal.class))).thenReturn(90);
        when(calculatorService.calculateBillableMinutes(anyInt(), anyInt())).thenReturn(90);
        when(calculatorService.calculateBaseValue(anyInt(), any(BigDecimal.class))).thenReturn(new BigDecimal("150.00"));

        BigDecimal horimetroFim = new BigDecimal("101.5");
        String checklistEntrada = null; // Missing checklist

        // When & Then: Should throw BusinessException
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            locacaoService.checkOut(tenantId, locacaoId, horimetroFim, null, checklistEntrada);
        });

        assertTrue(exception.getMessage().contains("checklist obrigatório"));
        assertTrue(exception.getMessage().contains("RN05"));
    }

    /**
     * RN05.2: Check-out deve falhar se checklist de entrada estiver vazio
     */
    @Test
    void testCheckOut_ShouldFailWhenChecklistBlank() {
        // Given: Locacao with blank checklist
        when(locacaoRepository.findByIdAndTenantId(locacaoId, tenantId))
            .thenReturn(Optional.of(locacao));
        when(jetskiService.findById(jetskiId)).thenReturn(jetski);
        when(modeloService.findById(jetski.getModeloId())).thenReturn(modelo);
        when(calculatorService.calculateUsedMinutes(any(BigDecimal.class), any(BigDecimal.class))).thenReturn(90);
        when(calculatorService.calculateBillableMinutes(anyInt(), anyInt())).thenReturn(90);
        when(calculatorService.calculateBaseValue(anyInt(), any(BigDecimal.class))).thenReturn(new BigDecimal("150.00"));

        BigDecimal horimetroFim = new BigDecimal("101.5");
        String checklistEntrada = "   "; // Blank checklist

        // When & Then: Should throw BusinessException
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            locacaoService.checkOut(tenantId, locacaoId, horimetroFim, null, checklistEntrada);
        });

        assertTrue(exception.getMessage().contains("checklist obrigatório"));
    }

    /**
     * RN05.3: Check-out deve falhar se fotos obrigatórias estiverem faltando
     */
    @Test
    void testCheckOut_ShouldFailWhenMandatoryPhotosMissing() {
        // Given: Locacao with checklist but missing photos
        when(locacaoRepository.findByIdAndTenantId(locacaoId, tenantId))
            .thenReturn(Optional.of(locacao));
        when(jetskiService.findById(jetskiId)).thenReturn(jetski);
        when(modeloService.findById(jetski.getModeloId())).thenReturn(modelo);
        when(calculatorService.calculateUsedMinutes(any(BigDecimal.class), any(BigDecimal.class))).thenReturn(90);
        when(calculatorService.calculateBillableMinutes(anyInt(), anyInt())).thenReturn(90);
        when(calculatorService.calculateBaseValue(anyInt(), any(BigDecimal.class))).thenReturn(new BigDecimal("150.00"));

        // Simulate photo validation failure
        doThrow(new BusinessException("Check-out requer 4 fotos obrigatórias. Faltando: CHECKOUT_HORIMETRO"))
            .when(photoValidationService).validateCheckOutPhotos(tenantId, locacaoId);

        BigDecimal horimetroFim = new BigDecimal("101.5");
        String checklistEntrada = "[\"motor_ok\",\"casco_ok\",\"limpeza_ok\"]";

        // When & Then: Should throw BusinessException from photo validation
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            locacaoService.checkOut(tenantId, locacaoId, horimetroFim, null, checklistEntrada);
        });

        assertTrue(exception.getMessage().contains("4 fotos obrigatórias"));
        verify(photoValidationService).validateCheckOutPhotos(tenantId, locacaoId);
    }

    /**
     * RN05.4: Check-out deve ter sucesso com checklist válido + 4 fotos
     */
    @Test
    void testCheckOut_ShouldSucceedWithValidChecklistAndPhotos() {
        // Given: Locacao with valid checklist and 4 photos
        when(locacaoRepository.findByIdAndTenantId(locacaoId, tenantId))
            .thenReturn(Optional.of(locacao));
        when(jetskiService.findById(jetskiId)).thenReturn(jetski);
        when(modeloService.findById(jetski.getModeloId())).thenReturn(modelo);
        when(calculatorService.calculateUsedMinutes(any(BigDecimal.class), any(BigDecimal.class))).thenReturn(90);
        when(calculatorService.calculateBillableMinutes(anyInt(), anyInt())).thenReturn(90);
        when(calculatorService.calculateBaseValue(anyInt(), any(BigDecimal.class))).thenReturn(new BigDecimal("150.00"));
        when(locacaoRepository.save(any(Locacao.class))).thenAnswer(i -> i.getArgument(0));
        when(jetskiService.updateJetski(eq(jetskiId), any(Jetski.class))).thenReturn(jetski);

        // Photo validation passes (all 4 photos present)
        doNothing().when(photoValidationService).validateCheckOutPhotos(tenantId, locacaoId);

        BigDecimal horimetroFim = new BigDecimal("101.5");
        String checklistEntrada = "[\"motor_ok\",\"casco_ok\",\"limpeza_ok\",\"avarias_nenhuma\"]";

        // When: Check-out with valid checklist + photos
        Locacao result = locacaoService.checkOut(tenantId, locacaoId, horimetroFim, null, checklistEntrada);

        // Then: Should complete successfully
        assertNotNull(result);
        assertEquals(LocacaoStatus.FINALIZADA, result.getStatus());
        assertEquals(checklistEntrada, result.getChecklistEntradaJson());
        assertNotNull(result.getDataCheckOut());
        assertEquals(new BigDecimal("150.00"), result.getValorBase());

        // Verify photo validation was called
        verify(photoValidationService).validateCheckOutPhotos(tenantId, locacaoId);

        // Verify jetski was released
        verify(jetskiService).updateStatus(jetskiId, JetskiStatus.DISPONIVEL);
    }

    /**
     * RN05.5: Check-in deve aceitar checklist de saída opcional
     */
    @Test
    void testCheckIn_ShouldAcceptOptionalChecklistSaida() {
        // Given: Valid reservation and jetski
        UUID reservaId = UUID.randomUUID();
        Reserva reserva = new Reserva();
        reserva.setId(reservaId);
        reserva.setTenantId(tenantId);
        reserva.setJetskiId(jetskiId);
        reserva.setClienteId(UUID.randomUUID());
        reserva.setStatus(Reserva.ReservaStatus.CONFIRMADA);
        reserva.setDataInicio(LocalDateTime.now());
        reserva.setDataFimPrevista(LocalDateTime.now().plusHours(2));

        jetski.setStatus(JetskiStatus.DISPONIVEL);

        when(reservaRepository.findById(reservaId)).thenReturn(Optional.of(reserva));
        when(jetskiService.findById(jetskiId)).thenReturn(jetski);
        when(locacaoRepository.existsByTenantIdAndJetskiIdAndStatus(tenantId, jetskiId, LocacaoStatus.EM_CURSO))
            .thenReturn(false);
        when(locacaoRepository.save(any(Locacao.class))).thenAnswer(i -> {
            Locacao saved = i.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));

        BigDecimal horimetroInicio = new BigDecimal("100.0");
        String checklistSaida = "[\"motor_ok\",\"casco_ok\",\"gasolina_ok\"]";

        // When: Check-in with checklist
        Locacao result = locacaoService.checkInFromReservation(
            tenantId, reservaId, horimetroInicio, null, checklistSaida
        );

        // Then: Should save checklist
        assertNotNull(result);
        assertEquals(LocacaoStatus.EM_CURSO, result.getStatus());
        assertEquals(checklistSaida, result.getChecklistSaidaJson());

        // Verify jetski was marked as LOCADO
        verify(jetskiService).updateStatus(jetskiId, JetskiStatus.LOCADO);
    }

    /**
     * RN05.6: Check-in deve aceitar checklist de saída como null
     */
    @Test
    void testCheckIn_ShouldAcceptNullChecklistSaida() {
        // Given: Valid reservation without checklist
        UUID reservaId = UUID.randomUUID();
        Reserva reserva = new Reserva();
        reserva.setId(reservaId);
        reserva.setTenantId(tenantId);
        reserva.setJetskiId(jetskiId);
        reserva.setClienteId(UUID.randomUUID());
        reserva.setStatus(Reserva.ReservaStatus.CONFIRMADA);
        reserva.setDataInicio(LocalDateTime.now());
        reserva.setDataFimPrevista(LocalDateTime.now().plusHours(2));

        jetski.setStatus(JetskiStatus.DISPONIVEL);

        when(reservaRepository.findById(reservaId)).thenReturn(Optional.of(reserva));
        when(jetskiService.findById(jetskiId)).thenReturn(jetski);
        when(locacaoRepository.existsByTenantIdAndJetskiIdAndStatus(tenantId, jetskiId, LocacaoStatus.EM_CURSO))
            .thenReturn(false);
        when(locacaoRepository.save(any(Locacao.class))).thenAnswer(i -> {
            Locacao saved = i.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));

        BigDecimal horimetroInicio = new BigDecimal("100.0");

        // When: Check-in without checklist (null)
        Locacao result = locacaoService.checkInFromReservation(
            tenantId, reservaId, horimetroInicio, null, null
        );

        // Then: Should succeed with null checklist
        assertNotNull(result);
        assertEquals(LocacaoStatus.EM_CURSO, result.getStatus());
        assertNull(result.getChecklistSaidaJson());
    }
}
