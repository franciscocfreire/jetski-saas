package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity: Locacao (Rental Operation)
 *
 * Represents the actual rental operation from check-in to check-out.
 * Tracks hourmeters, calculates billable time (with tolerance and rounding),
 * and computes final values.
 *
 * Lifecycle:
 * 1. Check-in: Create Locacao with horimetro_inicio, status = EM_CURSO
 * 2. Customer uses jetski
 * 3. Check-out: Register horimetro_fim, calculate values, status = FINALIZADA
 *
 * Two types of check-in:
 * 1. FROM RESERVATION: Links to existing Reserva (most common)
 * 2. WALK-IN: Direct check-in without prior reservation
 *
 * Business Rules Applied:
 * - RN01: Billable time = (used_minutes - tolerance) rounded to nearest 15min
 * - Tolerance (grace period): First X minutes are free (configured per modelo)
 * - Rounding: Always to nearest 15-minute block (ceil)
 * - Base value = (billable_minutes / 60) * price_per_hour
 *
 * Examples:
 * - Reservation #123 → Check-in (horimetro 100.0) → Check-out (horimetro 101.5)
 *   Used: 90 minutes, Tolerance: 5min, Billable: 90 minutes (rounded from 85)
 * - Walk-in customer → Direct check-in → 2-hour rental → Check-out
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Entity
@Table(name = "locacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Locacao {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Optional link to reservation (null for walk-in customers)
     */
    @Column(name = "reserva_id")
    private UUID reservaId;

    /**
     * Jetski being rented
     */
    @Column(name = "jetski_id", nullable = false)
    private UUID jetskiId;

    /**
     * Customer renting the jetski
     */
    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    /**
     * Seller/partner who facilitated the rental (optional)
     */
    @Column(name = "vendedor_id")
    private UUID vendedorId;

    // ===================================================================
    // Check-in Data
    // ===================================================================

    /**
     * Check-in timestamp (when customer takes the jetski)
     */
    @Column(name = "data_check_in", nullable = false)
    private LocalDateTime dataCheckIn;

    /**
     * Hourmeter reading at check-in (e.g., 100.5 hours)
     */
    @Column(name = "horimetro_inicio", nullable = false, precision = 10, scale = 2)
    private BigDecimal horimetroInicio;

    /**
     * Expected rental duration in minutes (for reference)
     */
    @Column(name = "duracao_prevista", nullable = false)
    private Integer duracaoPrevista;

    // ===================================================================
    // Check-out Data
    // ===================================================================

    /**
     * Check-out timestamp (when customer returns the jetski)
     */
    @Column(name = "data_check_out")
    private LocalDateTime dataCheckOut;

    /**
     * Hourmeter reading at check-out (e.g., 102.0 hours)
     */
    @Column(name = "horimetro_fim", precision = 10, scale = 2)
    private BigDecimal horimetroFim;

    /**
     * Actual minutes used (calculated from hourmeter difference)
     * minutes_used = (horimetro_fim - horimetro_inicio) * 60
     */
    @Column(name = "minutos_usados")
    private Integer minutosUsados;

    /**
     * Billable minutes after applying tolerance and rounding (RN01)
     * billable = ceil((used - tolerance) / 15) * 15
     */
    @Column(name = "minutos_faturaveis")
    private Integer minutosFaturaveis;

    // ===================================================================
    // Values
    // ===================================================================

    /**
     * Base rental value before taxes/discounts
     * valor_base = (minutos_faturaveis / 60.0) * preco_base_hora
     */
    @Column(name = "valor_base", precision = 10, scale = 2)
    private BigDecimal valorBase;

    /**
     * Final total value (after fuel, taxes, discounts, etc.)
     * For Sprint 2, valorTotal = valorBase (no fuel/commission yet)
     */
    @Column(name = "valor_total", precision = 10, scale = 2)
    private BigDecimal valorTotal;

    // ===================================================================
    // Status & Notes
    // ===================================================================

    /**
     * Current rental status
     */
    @Column(name = "status", nullable = false, length = 20)
    @Convert(converter = LocacaoStatusConverter.class)
    private LocacaoStatus status;

    /**
     * Additional notes or observations
     */
    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    // ===================================================================
    // Audit Fields
    // ===================================================================

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ===================================================================
    // Business Logic Helpers
    // ===================================================================

    /**
     * Check if rental is in progress (checked in but not checked out)
     */
    public boolean isEmCurso() {
        return this.status == LocacaoStatus.EM_CURSO;
    }

    /**
     * Check if rental is completed (checked out)
     */
    public boolean isFinalizada() {
        return this.status == LocacaoStatus.FINALIZADA;
    }

    /**
     * Check if checkout data is complete
     */
    public boolean isCheckoutComplete() {
        return this.dataCheckOut != null && this.horimetroFim != null;
    }

    /**
     * Check if this is a walk-in rental (no reservation)
     */
    public boolean isWalkIn() {
        return this.reservaId == null;
    }
}
