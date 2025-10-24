package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity: Reserva (Reservation)
 *
 * Represents a jetski reservation/booking made by a customer.
 * Reservations are now BY MODELO (not specific jetski).
 * Specific jetski is allocated at check-in time for maximum flexibility.
 *
 * Two types of reservations:
 * 1. WITH DEPOSIT (ALTA priority) - Guaranteed, blocks capacity
 * 2. WITHOUT DEPOSIT (BAIXA priority) - Overbooking allowed, first-come-first-served
 *
 * Examples:
 * - Maria Santos reserves "Yamaha VX Cruiser" modelo for 2025-02-15 10:00-12:00 (no deposit)
 * - Corporate event: 5 "Sea-Doo Spark" reservations WITH deposit (guaranteed)
 *
 * Business Rules:
 * - Reservation by MODELO (not specific jetski)
 * - Optional deposit system (ALTA vs BAIXA priority)
 * - Automatic expiration after grace period (no-show)
 * - Controlled overbooking for reservations without deposit
 * - Jetski allocated at check-in (FIFO for same modelo)
 *
 * @author Jetski Team
 * @since 0.2.0
 * @version 0.3.0 - Refactored to modelo-based booking with deposit system
 */
@Entity
@Table(name = "reserva")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reserva {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Modelo being reserved
     * Customer reserves a modelo (e.g., "Yamaha VX Cruiser"), not a specific jetski.
     * This allows fleet management flexibility - any jetski of this modelo can be assigned.
     */
    @Column(name = "modelo_id", nullable = false)
    private UUID modeloId;

    /**
     * Specific jetski allocated for this reservation (optional - allocated at check-in)
     * NULL until check-in or manual allocation by operator.
     * When customer arrives, operator assigns available jetski of the reserved modelo.
     *
     * Example workflow:
     * 1. Customer reserves "Yamaha VX Cruiser" → jetskiId = NULL
     * 2. Customer checks in → Operator assigns "YAMAHA-VX-001" → jetskiId = UUID
     */
    @Column(name = "jetski_id")
    private UUID jetskiId;

    /**
     * Customer making the reservation
     * Business rule RF03.4: Customer must have accepted terms before check-in
     */
    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    /**
     * Seller/partner who created the reservation (optional)
     * Used for commission calculation when reservation converts to rental
     */
    @Column(name = "vendedor_id")
    private UUID vendedorId;

    /**
     * Predicted start date/time
     * Used for schedule conflict detection
     */
    @Column(name = "data_inicio", nullable = false)
    private LocalDateTime dataInicio;

    /**
     * Predicted end date/time
     * Used for schedule conflict detection and availability planning
     */
    @Column(name = "data_fim_prevista", nullable = false)
    private LocalDateTime dataFimPrevista;

    /**
     * Priority level determines booking guarantee:
     * - ALTA: Customer paid deposit → guaranteed reservation, blocks capacity
     * - BAIXA: No deposit → overbooking allowed, first-come-first-served at check-in
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReservaPrioridade prioridade = ReservaPrioridade.BAIXA;

    /**
     * Whether customer paid deposit to guarantee reservation.
     * If true: ALTA priority (blocks capacity, guaranteed jetski)
     * If false: BAIXA priority (overbooking allowed, may not get jetski if fully booked)
     */
    @Column(name = "sinal_pago", nullable = false)
    @Builder.Default
    private Boolean sinalPago = false;

    /**
     * Deposit amount paid by customer.
     * Only set if sinalPago = true.
     * Typically calculated as: (estimated_rental_value * percentual_sinal / 100)
     */
    @Column(name = "valor_sinal", precision = 10, scale = 2)
    private BigDecimal valorSinal;

    /**
     * Timestamp when deposit was paid.
     * Only set if sinalPago = true.
     * Used for accounting and refund calculations.
     */
    @Column(name = "sinal_pago_em")
    private Instant sinalPagoEm;

    /**
     * Expiration timestamp for no-show handling.
     * Calculated as: data_inicio + grace_period (from tenant config)
     *
     * Example: If data_inicio = 10:00 and grace_period = 30min, then expira_em = 10:30
     * After 10:30, if customer didn't check-in:
     * - Without deposit: automatically mark as EXPIRADA
     * - With deposit: notify operator for manual decision (refund policy)
     */
    @Column(name = "expira_em")
    private LocalDateTime expiraEm;

    /**
     * Reservation status
     * Workflow: PENDENTE → CONFIRMADA → (check-in creates Locacao)
     * Can also transition to: CANCELADA, FINALIZADA, or EXPIRADA (no-show)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReservaStatus status = ReservaStatus.PENDENTE;

    /**
     * Additional notes or special requests
     * Examples: "Grupo de turistas", "Aniversário", "Primeira vez"
     */
    @Column(columnDefinition = "TEXT")
    private String observacoes;

    /**
     * Soft delete flag - inactive reservations are cancelled/archived
     * Preserves historical data for analytics
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Check if reservation can be confirmed
     * Business rules:
     * - Must be in PENDENTE status
     * - Must be active
     */
    public boolean canConfirm() {
        return ativo && status == ReservaStatus.PENDENTE;
    }

    /**
     * Check if reservation can be cancelled
     * Business rules:
     * - Must be PENDENTE or CONFIRMADA
     * - Must be active
     */
    public boolean canCancel() {
        return ativo && (status == ReservaStatus.PENDENTE || status == ReservaStatus.CONFIRMADA);
    }

    /**
     * Check if reservation is within scheduled period
     * Used for availability checking
     */
    public boolean isActive() {
        return ativo &&
               (status == ReservaStatus.PENDENTE || status == ReservaStatus.CONFIRMADA);
    }

    /**
     * Check if reservation is guaranteed (paid deposit).
     * Guaranteed reservations have ALTA priority and block capacity.
     *
     * @return true if reservation has deposit paid
     */
    public boolean isGarantida() {
        return Boolean.TRUE.equals(sinalPago) && prioridade == ReservaPrioridade.ALTA;
    }

    /**
     * Check if reservation has expired (no-show).
     * Compares current time with expiraEm timestamp.
     *
     * @return true if past expiration time
     */
    public boolean isExpirada() {
        return expiraEm != null && LocalDateTime.now().isAfter(expiraEm);
    }

    /**
     * Check if reservation can receive jetski allocation.
     * Requirements:
     * - Must be CONFIRMADA status
     * - No jetski assigned yet (jetskiId == null)
     * - Must be active
     *
     * @return true if ready for jetski allocation
     */
    public boolean podeSerAlocada() {
        return jetskiId == null &&
               status == ReservaStatus.CONFIRMADA &&
               Boolean.TRUE.equals(ativo);
    }

    /**
     * Check if deposit payment can be confirmed.
     * Requirements:
     * - Deposit not already paid
     * - Reservation is active (not cancelled/expired)
     * - Status is PENDENTE or CONFIRMADA
     *
     * @return true if can accept deposit payment
     */
    public boolean podeConfirmarSinal() {
        return !Boolean.TRUE.equals(sinalPago) &&
               Boolean.TRUE.equals(ativo) &&
               (status == ReservaStatus.PENDENTE || status == ReservaStatus.CONFIRMADA);
    }

    /**
     * Check if reservation should be auto-expired by job.
     * Criteria:
     * - Is expired (past grace period)
     * - No deposit paid (BAIXA priority)
     * - Status is still PENDENTE or CONFIRMADA
     * - Is active
     *
     * @return true if should be auto-expired
     */
    public boolean deveExpirar() {
        return isExpirada() &&
               !Boolean.TRUE.equals(sinalPago) &&
               (status == ReservaStatus.PENDENTE || status == ReservaStatus.CONFIRMADA) &&
               Boolean.TRUE.equals(ativo);
    }

    /**
     * Reservation status enum
     */
    public enum ReservaStatus {
        /**
         * Pending confirmation - initial state
         * Waiting for customer to pay deposit or operator to confirm
         */
        PENDENTE,

        /**
         * Confirmed reservation
         * For ALTA priority: jetski capacity is blocked
         * For BAIXA priority: overbooking still allowed
         */
        CONFIRMADA,

        /**
         * Cancelled by customer or operator before start time
         */
        CANCELADA,

        /**
         * Reservation completed (converted to rental at check-in)
         */
        FINALIZADA,

        /**
         * Expired due to no-show
         * Customer didn't check-in within grace period after start time
         */
        EXPIRADA
    }

    /**
     * Reservation priority enum
     * Determines booking guarantee and capacity management
     */
    public enum ReservaPrioridade {
        /**
         * HIGH priority - Customer paid deposit
         * Guarantees jetski availability, blocks capacity
         * Cannot be overbooking - hard limit on physical jetskis
         */
        ALTA,

        /**
         * LOW priority - No deposit paid
         * Overbooking allowed, first-come-first-served at check-in
         * May not get jetski if all are taken by ALTA or earlier arrivals
         */
        BAIXA
    }
}
