package com.jetski.manutencao.internal;

import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.JetskiStatus;
import com.jetski.locacoes.api.JetskiPublicService;
import com.jetski.manutencao.domain.OSManutencao;
import com.jetski.manutencao.domain.OSManutencaoPrioridade;
import com.jetski.manutencao.domain.OSManutencaoStatus;
import com.jetski.manutencao.domain.OSManutencaoTipo;
import com.jetski.manutencao.internal.repository.OSManutencaoRepository;
import com.jetski.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OSManutencaoService
 *
 * Tests maintenance order management and jetski status updates
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OSManutencaoService - Maintenance Order Management")
class OSManutencaoServiceTest {

    @Mock
    private OSManutencaoRepository osManutencaoRepository;

    @Mock
    private JetskiPublicService jetskiPublicService;

    @InjectMocks
    private OSManutencaoService service;

    private UUID tenantId;
    private UUID jetskiId;
    private UUID mecanicoId;
    private UUID osId;
    private Jetski jetski;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        jetskiId = UUID.randomUUID();
        mecanicoId = UUID.randomUUID();
        osId = UUID.randomUUID();

        jetski = Jetski.builder()
            .id(jetskiId)
            .tenantId(tenantId)
            .serie("ABC123")
            .horimetroAtual(new BigDecimal("125.5"))
            .status(JetskiStatus.DISPONIVEL)
            .ativo(true)
            .build();
    }

    // ===================================================================
    // Create Order Tests
    // ===================================================================

    @Test
    @DisplayName("Create OS: Should create preventive maintenance and block jetski")
    void testCreateOrder_Preventiva_ShouldBlockJetski() {
        // Given: Preventive maintenance OS
        OSManutencao os = OSManutencao.builder()
            .jetskiId(jetskiId)
            .mecanicoId(mecanicoId)
            .tipo(OSManutencaoTipo.PREVENTIVA)
            .prioridade(OSManutencaoPrioridade.MEDIA)
            .descricaoProblema("Manutenção preventiva de 50 horas")
            .horimetroAbertura(new BigDecimal("125.5"))
            .build();

        when(jetskiPublicService.findById(jetskiId)).thenReturn(jetski);
        when(osManutencaoRepository.save(any(OSManutencao.class)))
            .thenAnswer(invocation -> {
                OSManutencao saved = invocation.getArgument(0);
                saved.setId(osId);
                saved.setCreatedAt(Instant.now());
                saved.setUpdatedAt(Instant.now());
                return saved;
            });

        // When: Create order
        OSManutencao created = service.createOrder(os);

        // Then: Should save with correct values
        assertThat(created.getId()).isEqualTo(osId);
        assertThat(created.getStatus()).isEqualTo(OSManutencaoStatus.ABERTA);
        assertThat(created.getDtAbertura()).isNotNull();
        assertThat(created.getHorimetroAbertura()).isEqualByComparingTo(new BigDecimal("125.5"));

        // Then: Should block jetski (RN06)
        verify(jetskiPublicService).updateStatus(jetskiId, JetskiStatus.MANUTENCAO);
    }

    @Test
    @DisplayName("Create OS: Should fail if problem description is missing")
    void testCreateOrder_MissingDescription_ShouldThrow() {
        // Given: OS without problem description
        OSManutencao os = OSManutencao.builder()
            .jetskiId(jetskiId)
            .tipo(OSManutencaoTipo.CORRETIVA)
            .descricaoProblema("") // Empty!
            .build();

        when(jetskiPublicService.findById(jetskiId)).thenReturn(jetski);

        // When/Then: Should throw BusinessException
        assertThatThrownBy(() -> service.createOrder(os))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Descrição do problema é obrigatória");

        // Then: Should NOT save or block jetski
        verify(osManutencaoRepository, never()).save(any());
        verify(jetskiPublicService, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("Create OS: Should capture current odometer if not provided")
    void testCreateOrder_AutoCaptureOdometer() {
        // Given: OS without horimetroAbertura
        OSManutencao os = OSManutencao.builder()
            .jetskiId(jetskiId)
            .tipo(OSManutencaoTipo.REVISAO)
            .descricaoProblema("Revisão geral")
            .horimetroAbertura(null) // Not provided
            .build();

        when(jetskiPublicService.findById(jetskiId)).thenReturn(jetski);
        when(osManutencaoRepository.save(any(OSManutencao.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Create order
        service.createOrder(os);

        // Then: Should auto-capture odometer from jetski
        verify(osManutencaoRepository).save(argThat(saved ->
            saved.getHorimetroAbertura().compareTo(new BigDecimal("125.5")) == 0
        ));
    }

    // ===================================================================
    // Update Order Tests
    // ===================================================================

    @Test
    @DisplayName("Update OS: Should update fields successfully")
    void testUpdateOrder_Success() {
        // Given: Existing OS
        OSManutencao existing = OSManutencao.builder()
            .id(osId)
            .tenantId(tenantId)
            .jetskiId(jetskiId)
            .tipo(OSManutencaoTipo.CORRETIVA)
            .status(OSManutencaoStatus.EM_ANDAMENTO)
            .descricaoProblema("Motor falhando")
            .diagnostico("Vela desgastada")
            .valorPecas(BigDecimal.ZERO)
            .valorMaoObra(BigDecimal.ZERO)
            .build();

        OSManutencao updates = OSManutencao.builder()
            .diagnostico("Vela desgastada e filtro sujo")
            .solucao("Troca de vela e limpeza de filtro")
            .valorPecas(new BigDecimal("150.00"))
            .valorMaoObra(new BigDecimal("200.00"))
            .build();

        when(osManutencaoRepository.findById(osId)).thenReturn(Optional.of(existing));
        when(osManutencaoRepository.save(any(OSManutencao.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Update order
        OSManutencao updated = service.updateOrder(osId, updates);

        // Then: Should update fields
        assertThat(updated.getDiagnostico()).isEqualTo("Vela desgastada e filtro sujo");
        assertThat(updated.getSolucao()).isEqualTo("Troca de vela e limpeza de filtro");
        assertThat(updated.getValorPecas()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(updated.getValorMaoObra()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(updated.getValorTotal()).isEqualByComparingTo(new BigDecimal("350.00"));
    }

    @Test
    @DisplayName("Update OS: Should fail if order is finished")
    void testUpdateOrder_Finished_ShouldThrow() {
        // Given: Finished OS
        OSManutencao existing = OSManutencao.builder()
            .id(osId)
            .status(OSManutencaoStatus.CONCLUIDA)
            .build();

        OSManutencao updates = OSManutencao.builder()
            .diagnostico("New diagnosis")
            .build();

        when(osManutencaoRepository.findById(osId)).thenReturn(Optional.of(existing));

        // When/Then: Should throw BusinessException
        assertThatThrownBy(() -> service.updateOrder(osId, updates))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Não é possível alterar ordem de serviço finalizada");

        verify(osManutencaoRepository, never()).save(any());
    }

    // ===================================================================
    // Start Order Tests
    // ===================================================================

    @Test
    @DisplayName("Start OS: Should transition from ABERTA to EM_ANDAMENTO")
    void testStartOrder_Success() {
        // Given: OS in ABERTA status
        OSManutencao os = OSManutencao.builder()
            .id(osId)
            .status(OSManutencaoStatus.ABERTA)
            .build();

        when(osManutencaoRepository.findById(osId)).thenReturn(Optional.of(os));
        when(osManutencaoRepository.save(any(OSManutencao.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Start order
        OSManutencao started = service.startOrder(osId);

        // Then: Should transition to EM_ANDAMENTO
        assertThat(started.getStatus()).isEqualTo(OSManutencaoStatus.EM_ANDAMENTO);
        assertThat(started.getDtInicioReal()).isNotNull();
    }

    @Test
    @DisplayName("Start OS: Should fail if not in ABERTA status")
    void testStartOrder_NotAberta_ShouldThrow() {
        // Given: OS already in EM_ANDAMENTO
        OSManutencao os = OSManutencao.builder()
            .id(osId)
            .status(OSManutencaoStatus.EM_ANDAMENTO)
            .build();

        when(osManutencaoRepository.findById(osId)).thenReturn(Optional.of(os));

        // When/Then: Should throw BusinessException
        assertThatThrownBy(() -> service.startOrder(osId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Ordem de serviço deve estar ABERTA para iniciar");
    }

    // ===================================================================
    // Finish Order Tests
    // ===================================================================

    @Test
    @DisplayName("Finish OS: Should complete and release jetski (no other active OS)")
    void testFinishOrder_ShouldReleaseJetski() {
        // Given: OS in EM_ANDAMENTO
        OSManutencao os = OSManutencao.builder()
            .id(osId)
            .jetskiId(jetskiId)
            .status(OSManutencaoStatus.EM_ANDAMENTO)
            .build();

        jetski.setStatus(JetskiStatus.MANUTENCAO);

        com.jetski.manutencao.api.dto.OSManutencaoFinishRequest finishRequest =
            com.jetski.manutencao.api.dto.OSManutencaoFinishRequest.builder()
                .horimetroFechamento(new java.math.BigDecimal("126.5"))
                .valorPecas(new java.math.BigDecimal("250.00"))
                .valorMaoObra(new java.math.BigDecimal("150.00"))
                .build();

        when(osManutencaoRepository.findById(osId)).thenReturn(Optional.of(os));
        when(jetskiPublicService.findById(jetskiId)).thenReturn(jetski);
        when(osManutencaoRepository.hasActiveMaintenanceByJetskiId(jetskiId)).thenReturn(false);
        when(osManutencaoRepository.save(any(OSManutencao.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Finish order
        OSManutencao finished = service.finishOrder(osId, finishRequest);

        // Then: Should transition to CONCLUIDA
        assertThat(finished.getStatus()).isEqualTo(OSManutencaoStatus.CONCLUIDA);
        assertThat(finished.getDtConclusao()).isNotNull();
        assertThat(finished.getHorimetroConclusao()).isEqualTo(new java.math.BigDecimal("126.5"));
        assertThat(finished.getValorPecas()).isEqualTo(new java.math.BigDecimal("250.00"));
        assertThat(finished.getValorMaoObra()).isEqualTo(new java.math.BigDecimal("150.00"));
        assertThat(finished.getValorTotal()).isEqualTo(new java.math.BigDecimal("400.00"));

        // Then: Should release jetski (RN06)
        verify(jetskiPublicService).updateStatus(jetskiId, JetskiStatus.DISPONIVEL);
    }

    @Test
    @DisplayName("Finish OS: Should NOT release jetski if other active OS exists")
    void testFinishOrder_OtherActiveOS_ShouldNotReleaseJetski() {
        // Given: OS in EM_ANDAMENTO, but other OS exists
        OSManutencao os = OSManutencao.builder()
            .id(osId)
            .jetskiId(jetskiId)
            .status(OSManutencaoStatus.EM_ANDAMENTO)
            .build();

        com.jetski.manutencao.api.dto.OSManutencaoFinishRequest finishRequest =
            com.jetski.manutencao.api.dto.OSManutencaoFinishRequest.builder()
                .horimetroFechamento(new java.math.BigDecimal("126.5"))
                .valorPecas(new java.math.BigDecimal("250.00"))
                .valorMaoObra(new java.math.BigDecimal("150.00"))
                .build();

        when(osManutencaoRepository.findById(osId)).thenReturn(Optional.of(os));
        when(osManutencaoRepository.hasActiveMaintenanceByJetskiId(jetskiId)).thenReturn(true);
        when(osManutencaoRepository.save(any(OSManutencao.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Finish order
        service.finishOrder(osId, finishRequest);

        // Then: Should NOT release jetski
        verify(jetskiPublicService, never()).updateStatus(eq(jetskiId), eq(JetskiStatus.DISPONIVEL));
    }

    // ===================================================================
    // Cancel Order Tests
    // ===================================================================

    @Test
    @DisplayName("Cancel OS: Should cancel and release jetski")
    void testCancelOrder_Success() {
        // Given: OS in ABERTA
        OSManutencao os = OSManutencao.builder()
            .id(osId)
            .jetskiId(jetskiId)
            .status(OSManutencaoStatus.ABERTA)
            .build();

        // Jetski is currently in MANUTENCAO (will be released)
        jetski.setStatus(JetskiStatus.MANUTENCAO);

        when(osManutencaoRepository.findById(osId)).thenReturn(Optional.of(os));
        when(osManutencaoRepository.hasActiveMaintenanceByJetskiId(jetskiId)).thenReturn(false);
        when(osManutencaoRepository.save(any(OSManutencao.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(jetskiPublicService.findById(jetskiId)).thenReturn(jetski);

        // When: Cancel order
        OSManutencao cancelled = service.cancelOrder(osId);

        // Then: Should transition to CANCELADA
        assertThat(cancelled.getStatus()).isEqualTo(OSManutencaoStatus.CANCELADA);

        // Then: Should release jetski
        verify(jetskiPublicService).updateStatus(jetskiId, JetskiStatus.DISPONIVEL);
    }

    @Test
    @DisplayName("Cancel OS: Should fail if already finished")
    void testCancelOrder_AlreadyFinished_ShouldThrow() {
        // Given: OS already CONCLUIDA
        OSManutencao os = OSManutencao.builder()
            .id(osId)
            .status(OSManutencaoStatus.CONCLUIDA)
            .build();

        when(osManutencaoRepository.findById(osId)).thenReturn(Optional.of(os));

        // When/Then: Should throw BusinessException
        assertThatThrownBy(() -> service.cancelOrder(osId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Não é possível cancelar ordem de serviço finalizada");
    }

    // ===================================================================
    // Wait for Parts / Resume Tests
    // ===================================================================

    @Test
    @DisplayName("Wait for Parts: Should transition from EM_ANDAMENTO to AGUARDANDO_PECAS")
    void testWaitForParts_Success() {
        // Given: OS in EM_ANDAMENTO
        OSManutencao os = OSManutencao.builder()
            .id(osId)
            .status(OSManutencaoStatus.EM_ANDAMENTO)
            .build();

        when(osManutencaoRepository.findById(osId)).thenReturn(Optional.of(os));
        when(osManutencaoRepository.save(any(OSManutencao.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Mark as waiting for parts
        OSManutencao waiting = service.waitForParts(osId);

        // Then: Should transition to AGUARDANDO_PECAS
        assertThat(waiting.getStatus()).isEqualTo(OSManutencaoStatus.AGUARDANDO_PECAS);
    }

    @Test
    @DisplayName("Resume: Should transition from AGUARDANDO_PECAS to EM_ANDAMENTO")
    void testResumeOrder_Success() {
        // Given: OS in AGUARDANDO_PECAS
        OSManutencao os = OSManutencao.builder()
            .id(osId)
            .status(OSManutencaoStatus.AGUARDANDO_PECAS)
            .build();

        when(osManutencaoRepository.findById(osId)).thenReturn(Optional.of(os));
        when(osManutencaoRepository.save(any(OSManutencao.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Resume work
        OSManutencao resumed = service.resumeOrder(osId);

        // Then: Should transition to EM_ANDAMENTO
        assertThat(resumed.getStatus()).isEqualTo(OSManutencaoStatus.EM_ANDAMENTO);
    }

    // ===================================================================
    // Query Tests
    // ===================================================================

    @Test
    @DisplayName("List Active Orders: Should return only active statuses")
    void testListActiveOrders() {
        // Given: Active orders
        List<OSManutencao> activeOrders = List.of(
            OSManutencao.builder().id(UUID.randomUUID()).status(OSManutencaoStatus.ABERTA).build(),
            OSManutencao.builder().id(UUID.randomUUID()).status(OSManutencaoStatus.EM_ANDAMENTO).build()
        );

        when(osManutencaoRepository.findAllActive()).thenReturn(activeOrders);

        // When: List active orders
        List<OSManutencao> result = service.listActiveOrders();

        // Then: Should return active orders
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(OSManutencao::isActive);
    }

    @Test
    @DisplayName("Has Active Maintenance: Should return true if active OS exists")
    void testHasActiveMaintenance_True() {
        // Given: Jetski has active OS
        when(osManutencaoRepository.hasActiveMaintenanceByJetskiId(jetskiId)).thenReturn(true);

        // When: Check if has active maintenance
        boolean result = service.hasActiveMaintenance(jetskiId);

        // Then: Should return true
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Has Active Maintenance: Should return false if no active OS")
    void testHasActiveMaintenance_False() {
        // Given: Jetski has NO active OS
        when(osManutencaoRepository.hasActiveMaintenanceByJetskiId(jetskiId)).thenReturn(false);

        // When: Check if has active maintenance
        boolean result = service.hasActiveMaintenance(jetskiId);

        // Then: Should return false
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Find by ID: Should return OS if exists")
    void testFindById_Success() {
        // Given: OS exists
        OSManutencao os = OSManutencao.builder()
            .id(osId)
            .jetskiId(jetskiId)
            .build();

        when(osManutencaoRepository.findById(osId)).thenReturn(Optional.of(os));

        // When: Find by ID
        OSManutencao found = service.findById(osId);

        // Then: Should return OS
        assertThat(found.getId()).isEqualTo(osId);
    }

    @Test
    @DisplayName("Find by ID: Should throw if not found")
    void testFindById_NotFound_ShouldThrow() {
        // Given: OS does NOT exist
        when(osManutencaoRepository.findById(osId)).thenReturn(Optional.empty());

        // When/Then: Should throw BusinessException
        assertThatThrownBy(() -> service.findById(osId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Ordem de serviço não encontrada");
    }
}
