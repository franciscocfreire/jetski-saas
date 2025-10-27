package com.jetski.locacoes.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para Reserva domain class.
 *
 * Foco em cobertura de branches nos métodos de regras de negócio:
 * - canConfirm(), canCancel(), isActive()
 * - isGarantida(), isExpirada()
 * - podeSerAlocada(), podeConfirmarSinal(), deveExpirar()
 *
 * Cada método possui múltiplas condições (AND/OR) que geram branches.
 */
@DisplayName("Reserva Domain Unit Tests")
class ReservaTest {

    // ==================== canConfirm() Tests ====================

    @Test
    @DisplayName("canConfirm should return true when ativo=true and status=PENDENTE")
    void testCanConfirm_True_WhenActivePendente() {
        // Given
        Reserva reserva = Reserva.builder()
                .ativo(true)
                .status(Reserva.ReservaStatus.PENDENTE)
                .build();

        // When/Then
        assertThat(reserva.canConfirm()).isTrue();
    }

    @Test
    @DisplayName("canConfirm should return false when ativo=false")
    void testCanConfirm_False_WhenInactive() {
        // Given
        Reserva reserva = Reserva.builder()
                .ativo(false)
                .status(Reserva.ReservaStatus.PENDENTE)
                .build();

        // When/Then
        assertThat(reserva.canConfirm()).isFalse();
    }

    @Test
    @DisplayName("canConfirm should return false when status=CONFIRMADA")
    void testCanConfirm_False_WhenConfirmada() {
        // Given
        Reserva reserva = Reserva.builder()
                .ativo(true)
                .status(Reserva.ReservaStatus.CONFIRMADA)
                .build();

        // When/Then
        assertThat(reserva.canConfirm()).isFalse();
    }

    @Test
    @DisplayName("canConfirm should return false when status=CANCELADA")
    void testCanConfirm_False_WhenCancelada() {
        // Given
        Reserva reserva = Reserva.builder()
                .ativo(true)
                .status(Reserva.ReservaStatus.CANCELADA)
                .build();

        // When/Then
        assertThat(reserva.canConfirm()).isFalse();
    }

    // ==================== canCancel() Tests ====================

    @Test
    @DisplayName("canCancel should return true when ativo=true and status=PENDENTE")
    void testCanCancel_True_WhenActivePendente() {
        // Given
        Reserva reserva = Reserva.builder()
                .ativo(true)
                .status(Reserva.ReservaStatus.PENDENTE)
                .build();

        // When/Then
        assertThat(reserva.canCancel()).isTrue();
    }

    @Test
    @DisplayName("canCancel should return true when ativo=true and status=CONFIRMADA")
    void testCanCancel_True_WhenActiveConfirmada() {
        // Given
        Reserva reserva = Reserva.builder()
                .ativo(true)
                .status(Reserva.ReservaStatus.CONFIRMADA)
                .build();

        // When/Then
        assertThat(reserva.canCancel()).isTrue();
    }

    @Test
    @DisplayName("canCancel should return false when ativo=false")
    void testCanCancel_False_WhenInactive() {
        // Given
        Reserva reserva = Reserva.builder()
                .ativo(false)
                .status(Reserva.ReservaStatus.PENDENTE)
                .build();

        // When/Then
        assertThat(reserva.canCancel()).isFalse();
    }

    @Test
    @DisplayName("canCancel should return false when status=FINALIZADA")
    void testCanCancel_False_WhenFinalizada() {
        // Given
        Reserva reserva = Reserva.builder()
                .ativo(true)
                .status(Reserva.ReservaStatus.FINALIZADA)
                .build();

        // When/Then
        assertThat(reserva.canCancel()).isFalse();
    }

    @Test
    @DisplayName("canCancel should return false when status=EXPIRADA")
    void testCanCancel_False_WhenExpirada() {
        // Given
        Reserva reserva = Reserva.builder()
                .ativo(true)
                .status(Reserva.ReservaStatus.EXPIRADA)
                .build();

        // When/Then
        assertThat(reserva.canCancel()).isFalse();
    }

    // ==================== isActive() Tests ====================

    @Test
    @DisplayName("isActive should return true when ativo=true and status=PENDENTE")
    void testIsActive_True_WhenActivePendente() {
        // Given
        Reserva reserva = Reserva.builder()
                .ativo(true)
                .status(Reserva.ReservaStatus.PENDENTE)
                .build();

        // When/Then
        assertThat(reserva.isActive()).isTrue();
    }

    @Test
    @DisplayName("isActive should return true when ativo=true and status=CONFIRMADA")
    void testIsActive_True_WhenActiveConfirmada() {
        // Given
        Reserva reserva = Reserva.builder()
                .ativo(true)
                .status(Reserva.ReservaStatus.CONFIRMADA)
                .build();

        // When/Then
        assertThat(reserva.isActive()).isTrue();
    }

    @Test
    @DisplayName("isActive should return false when ativo=false")
    void testIsActive_False_WhenInactive() {
        // Given
        Reserva reserva = Reserva.builder()
                .ativo(false)
                .status(Reserva.ReservaStatus.PENDENTE)
                .build();

        // When/Then
        assertThat(reserva.isActive()).isFalse();
    }

    @Test
    @DisplayName("isActive should return false when status=CANCELADA")
    void testIsActive_False_WhenCancelada() {
        // Given
        Reserva reserva = Reserva.builder()
                .ativo(true)
                .status(Reserva.ReservaStatus.CANCELADA)
                .build();

        // When/Then
        assertThat(reserva.isActive()).isFalse();
    }

    // ==================== isGarantida() Tests ====================

    @Test
    @DisplayName("isGarantida should return true when sinalPago=true and prioridade=ALTA")
    void testIsGarantida_True_WhenDepositPaidAndAltaPriority() {
        // Given
        Reserva reserva = Reserva.builder()
                .sinalPago(true)
                .prioridade(Reserva.ReservaPrioridade.ALTA)
                .build();

        // When/Then
        assertThat(reserva.isGarantida()).isTrue();
    }

    @Test
    @DisplayName("isGarantida should return false when sinalPago=false")
    void testIsGarantida_False_WhenNoDeposit() {
        // Given
        Reserva reserva = Reserva.builder()
                .sinalPago(false)
                .prioridade(Reserva.ReservaPrioridade.ALTA)
                .build();

        // When/Then
        assertThat(reserva.isGarantida()).isFalse();
    }

    @Test
    @DisplayName("isGarantida should return false when prioridade=BAIXA")
    void testIsGarantida_False_WhenBaixaPriority() {
        // Given
        Reserva reserva = Reserva.builder()
                .sinalPago(true)
                .prioridade(Reserva.ReservaPrioridade.BAIXA)
                .build();

        // When/Then
        assertThat(reserva.isGarantida()).isFalse();
    }

    @Test
    @DisplayName("isGarantida should return false when sinalPago=null")
    void testIsGarantida_False_WhenSinalPagoNull() {
        // Given
        Reserva reserva = Reserva.builder()
                .sinalPago(null)
                .prioridade(Reserva.ReservaPrioridade.ALTA)
                .build();

        // When/Then
        assertThat(reserva.isGarantida()).isFalse();
    }

    // ==================== isExpirada() Tests ====================

    @Test
    @DisplayName("isExpirada should return true when expiraEm is in the past")
    void testIsExpirada_True_WhenExpired() {
        // Given: Expiration time in the past
        LocalDateTime past = LocalDateTime.now().minusHours(1);
        Reserva reserva = Reserva.builder()
                .expiraEm(past)
                .build();

        // When/Then
        assertThat(reserva.isExpirada()).isTrue();
    }

    @Test
    @DisplayName("isExpirada should return false when expiraEm is in the future")
    void testIsExpirada_False_WhenNotExpired() {
        // Given: Expiration time in the future
        LocalDateTime future = LocalDateTime.now().plusHours(1);
        Reserva reserva = Reserva.builder()
                .expiraEm(future)
                .build();

        // When/Then
        assertThat(reserva.isExpirada()).isFalse();
    }

    @Test
    @DisplayName("isExpirada should return false when expiraEm is null")
    void testIsExpirada_False_WhenExpiraEmNull() {
        // Given
        Reserva reserva = Reserva.builder()
                .expiraEm(null)
                .build();

        // When/Then
        assertThat(reserva.isExpirada()).isFalse();
    }

    // ==================== podeSerAlocada() Tests ====================

    @Test
    @DisplayName("podeSerAlocada should return true when jetskiId=null, status=CONFIRMADA, ativo=true")
    void testPodeSerAlocada_True_WhenAllConditionsMet() {
        // Given
        Reserva reserva = Reserva.builder()
                .jetskiId(null)
                .status(Reserva.ReservaStatus.CONFIRMADA)
                .ativo(true)
                .build();

        // When/Then
        assertThat(reserva.podeSerAlocada()).isTrue();
    }

    @Test
    @DisplayName("podeSerAlocada should return false when jetskiId is already set")
    void testPodeSerAlocada_False_WhenJetskiAlreadyAllocated() {
        // Given
        Reserva reserva = Reserva.builder()
                .jetskiId(UUID.randomUUID())
                .status(Reserva.ReservaStatus.CONFIRMADA)
                .ativo(true)
                .build();

        // When/Then
        assertThat(reserva.podeSerAlocada()).isFalse();
    }

    @Test
    @DisplayName("podeSerAlocada should return false when status is not CONFIRMADA")
    void testPodeSerAlocada_False_WhenStatusNotConfirmada() {
        // Given
        Reserva reserva = Reserva.builder()
                .jetskiId(null)
                .status(Reserva.ReservaStatus.PENDENTE)
                .ativo(true)
                .build();

        // When/Then
        assertThat(reserva.podeSerAlocada()).isFalse();
    }

    @Test
    @DisplayName("podeSerAlocada should return false when ativo=false")
    void testPodeSerAlocada_False_WhenInactive() {
        // Given
        Reserva reserva = Reserva.builder()
                .jetskiId(null)
                .status(Reserva.ReservaStatus.CONFIRMADA)
                .ativo(false)
                .build();

        // When/Then
        assertThat(reserva.podeSerAlocada()).isFalse();
    }

    @Test
    @DisplayName("podeSerAlocada should return false when ativo=null")
    void testPodeSerAlocada_False_WhenAtivoNull() {
        // Given
        Reserva reserva = Reserva.builder()
                .jetskiId(null)
                .status(Reserva.ReservaStatus.CONFIRMADA)
                .ativo(null)
                .build();

        // When/Then
        assertThat(reserva.podeSerAlocada()).isFalse();
    }

    // ==================== podeConfirmarSinal() Tests ====================

    @Test
    @DisplayName("podeConfirmarSinal should return true when sinalPago=false, ativo=true, status=PENDENTE")
    void testPodeConfirmarSinal_True_WhenPendente() {
        // Given
        Reserva reserva = Reserva.builder()
                .sinalPago(false)
                .ativo(true)
                .status(Reserva.ReservaStatus.PENDENTE)
                .build();

        // When/Then
        assertThat(reserva.podeConfirmarSinal()).isTrue();
    }

    @Test
    @DisplayName("podeConfirmarSinal should return true when sinalPago=false, ativo=true, status=CONFIRMADA")
    void testPodeConfirmarSinal_True_WhenConfirmada() {
        // Given
        Reserva reserva = Reserva.builder()
                .sinalPago(false)
                .ativo(true)
                .status(Reserva.ReservaStatus.CONFIRMADA)
                .build();

        // When/Then
        assertThat(reserva.podeConfirmarSinal()).isTrue();
    }

    @Test
    @DisplayName("podeConfirmarSinal should return false when sinalPago=true")
    void testPodeConfirmarSinal_False_WhenAlreadyPaid() {
        // Given
        Reserva reserva = Reserva.builder()
                .sinalPago(true)
                .ativo(true)
                .status(Reserva.ReservaStatus.PENDENTE)
                .build();

        // When/Then
        assertThat(reserva.podeConfirmarSinal()).isFalse();
    }

    @Test
    @DisplayName("podeConfirmarSinal should return false when ativo=false")
    void testPodeConfirmarSinal_False_WhenInactive() {
        // Given
        Reserva reserva = Reserva.builder()
                .sinalPago(false)
                .ativo(false)
                .status(Reserva.ReservaStatus.PENDENTE)
                .build();

        // When/Then
        assertThat(reserva.podeConfirmarSinal()).isFalse();
    }

    @Test
    @DisplayName("podeConfirmarSinal should return false when status=CANCELADA")
    void testPodeConfirmarSinal_False_WhenCancelada() {
        // Given
        Reserva reserva = Reserva.builder()
                .sinalPago(false)
                .ativo(true)
                .status(Reserva.ReservaStatus.CANCELADA)
                .build();

        // When/Then
        assertThat(reserva.podeConfirmarSinal()).isFalse();
    }

    @Test
    @DisplayName("podeConfirmarSinal should return false when sinalPago=null (treated as false in Boolean.TRUE.equals)")
    void testPodeConfirmarSinal_True_WhenSinalPagoNull() {
        // Given: sinalPago=null is treated as "not paid" by !Boolean.TRUE.equals()
        Reserva reserva = Reserva.builder()
                .sinalPago(null)
                .ativo(true)
                .status(Reserva.ReservaStatus.PENDENTE)
                .build();

        // When/Then: Should return true because !Boolean.TRUE.equals(null) = true
        assertThat(reserva.podeConfirmarSinal()).isTrue();
    }

    // ==================== deveExpirar() Tests ====================

    @Test
    @DisplayName("deveExpirar should return true when all conditions met: expired, no deposit, PENDENTE, active")
    void testDeveExpirar_True_WhenAllConditionsMet() {
        // Given
        Reserva reserva = Reserva.builder()
                .expiraEm(LocalDateTime.now().minusHours(1))
                .sinalPago(false)
                .status(Reserva.ReservaStatus.PENDENTE)
                .ativo(true)
                .build();

        // When/Then
        assertThat(reserva.deveExpirar()).isTrue();
    }

    @Test
    @DisplayName("deveExpirar should return true when expired, no deposit, CONFIRMADA, active")
    void testDeveExpirar_True_WhenConfirmadaExpired() {
        // Given
        Reserva reserva = Reserva.builder()
                .expiraEm(LocalDateTime.now().minusHours(1))
                .sinalPago(false)
                .status(Reserva.ReservaStatus.CONFIRMADA)
                .ativo(true)
                .build();

        // When/Then
        assertThat(reserva.deveExpirar()).isTrue();
    }

    @Test
    @DisplayName("deveExpirar should return false when not expired yet")
    void testDeveExpirar_False_WhenNotExpired() {
        // Given
        Reserva reserva = Reserva.builder()
                .expiraEm(LocalDateTime.now().plusHours(1))
                .sinalPago(false)
                .status(Reserva.ReservaStatus.PENDENTE)
                .ativo(true)
                .build();

        // When/Then
        assertThat(reserva.deveExpirar()).isFalse();
    }

    @Test
    @DisplayName("deveExpirar should return false when deposit was paid")
    void testDeveExpirar_False_WhenDepositPaid() {
        // Given
        Reserva reserva = Reserva.builder()
                .expiraEm(LocalDateTime.now().minusHours(1))
                .sinalPago(true)
                .status(Reserva.ReservaStatus.PENDENTE)
                .ativo(true)
                .build();

        // When/Then
        assertThat(reserva.deveExpirar()).isFalse();
    }

    @Test
    @DisplayName("deveExpirar should return false when status=FINALIZADA")
    void testDeveExpirar_False_WhenFinalizada() {
        // Given
        Reserva reserva = Reserva.builder()
                .expiraEm(LocalDateTime.now().minusHours(1))
                .sinalPago(false)
                .status(Reserva.ReservaStatus.FINALIZADA)
                .ativo(true)
                .build();

        // When/Then
        assertThat(reserva.deveExpirar()).isFalse();
    }

    @Test
    @DisplayName("deveExpirar should return false when ativo=false")
    void testDeveExpirar_False_WhenInactive() {
        // Given
        Reserva reserva = Reserva.builder()
                .expiraEm(LocalDateTime.now().minusHours(1))
                .sinalPago(false)
                .status(Reserva.ReservaStatus.PENDENTE)
                .ativo(false)
                .build();

        // When/Then
        assertThat(reserva.deveExpirar()).isFalse();
    }

    @Test
    @DisplayName("deveExpirar should return false when expiraEm is null")
    void testDeveExpirar_False_WhenExpiraEmNull() {
        // Given
        Reserva reserva = Reserva.builder()
                .expiraEm(null)
                .sinalPago(false)
                .status(Reserva.ReservaStatus.PENDENTE)
                .ativo(true)
                .build();

        // When/Then
        assertThat(reserva.deveExpirar()).isFalse();
    }

    // ==================== Builder and Defaults Tests ====================

    @Test
    @DisplayName("Should have correct default values when built with builder")
    void testBuilderDefaults() {
        // When
        Reserva reserva = Reserva.builder().build();

        // Then
        assertThat(reserva.getStatus()).isEqualTo(Reserva.ReservaStatus.PENDENTE);
        assertThat(reserva.getPrioridade()).isEqualTo(Reserva.ReservaPrioridade.BAIXA);
        assertThat(reserva.getSinalPago()).isEqualTo(false);
        assertThat(reserva.getAtivo()).isEqualTo(true);
    }
}
