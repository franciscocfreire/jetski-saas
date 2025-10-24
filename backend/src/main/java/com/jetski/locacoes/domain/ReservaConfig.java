package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity: ReservaConfig (Reservation Configuration)
 *
 * Per-tenant configuration for reservation/booking policies.
 * Controls behavior for:
 * - Grace periods (how long before no-show expiration)
 * - Deposit requirements (% of estimated rental value)
 * - Overbooking limits (how many reservations without deposit)
 * - Notification settings
 *
 * Examples:
 * - Tenant A: 30min grace period, 30% deposit, 2x overbooking
 * - Tenant B: 15min grace period, 50% deposit, no overbooking
 *
 * Business Rules:
 * - Grace period must be > 0 minutes
 * - Deposit percentage: 0-100%
 * - Overbooking factor: >= 1.0 (1.0 = no overbooking, 2.0 = double)
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Entity
@Table(name = "reserva_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaConfig {

    /**
     * Tenant ID (Primary Key)
     * One configuration per tenant
     */
    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    /**
     * Grace period in minutes before reservation expires.
     * After this period past the scheduled start time, reservation
     * is automatically marked as EXPIRADA if customer didn't check-in.
     *
     * Example: 30 means customer has 30 minutes after start time to arrive
     */
    @Column(name = "grace_period_minutos", nullable = false)
    @Builder.Default
    private Integer gracePeriodMinutos = 30;

    /**
     * Deposit percentage (0-100%).
     * When customer pays deposit, they get ALTA priority (guaranteed reservation).
     *
     * Calculation: deposit_value = estimated_rental_value * (percentual_sinal / 100)
     *
     * Example: 30.00 = customer pays 30% of estimated rental as deposit
     */
    @Column(name = "percentual_sinal", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal percentualSinal = new BigDecimal("30.00");

    /**
     * Overbooking factor for reservations without deposit.
     * Determines how many LOW priority reservations to accept.
     *
     * Calculation: max_reservations = num_jetskis * fator_overbooking
     *
     * Examples:
     * - 1.0 = no overbooking (accept only up to available jetskis)
     * - 1.5 = accept 50% more (3 jetskis = accept 4-5 reservations)
     * - 2.0 = double booking (3 jetskis = accept 6 reservations)
     */
    @Column(name = "fator_overbooking", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal fatorOverbooking = new BigDecimal("1.5");

    /**
     * Maximum reservations without deposit per modelo/timeslot.
     * Absolute cap regardless of overbooking factor.
     *
     * Example: 8 = max 8 reservations without deposit for same modelo/time
     */
    @Column(name = "max_reservas_sem_sinal_por_modelo", nullable = false)
    @Builder.Default
    private Integer maxReservasSemSinalPorModelo = 8;

    /**
     * Enable customer notifications before expiration.
     * If true, system will notify customer N minutes before expiration.
     */
    @Column(name = "notificar_antes_expiracao", nullable = false)
    @Builder.Default
    private Boolean notificarAntesExpiracao = true;

    /**
     * Minutes before expiration to notify customer.
     * Only used if notificarAntesExpiracao = true.
     *
     * Example: 15 = notify customer 15 minutes before reservation expires
     */
    @Column(name = "notificar_minutos_antecedencia", nullable = false)
    @Builder.Default
    private Integer notificarMinutosAntecedencia = 15;

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
     * Validate configuration values are within acceptable ranges.
     *
     * @throws IllegalArgumentException if any value is invalid
     */
    public void validate() {
        if (gracePeriodMinutos == null || gracePeriodMinutos <= 0) {
            throw new IllegalArgumentException("Grace period deve ser maior que zero");
        }

        if (percentualSinal == null ||
            percentualSinal.compareTo(BigDecimal.ZERO) < 0 ||
            percentualSinal.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Percentual de sinal deve estar entre 0 e 100");
        }

        if (fatorOverbooking == null ||
            fatorOverbooking.compareTo(new BigDecimal("1.0")) < 0) {
            throw new IllegalArgumentException("Fator de overbooking deve ser >= 1.0");
        }

        if (maxReservasSemSinalPorModelo == null || maxReservasSemSinalPorModelo <= 0) {
            throw new IllegalArgumentException("Máximo de reservas sem sinal deve ser maior que zero");
        }

        if (notificarAntesExpiracao && (notificarMinutosAntecedencia == null || notificarMinutosAntecedencia <= 0)) {
            throw new IllegalArgumentException("Minutos de antecedência para notificação deve ser maior que zero");
        }
    }

    /**
     * Check if overbooking is enabled (factor > 1.0).
     *
     * @return true if overbooking is allowed
     */
    public boolean isOverbookingEnabled() {
        return fatorOverbooking != null && fatorOverbooking.compareTo(new BigDecimal("1.0")) > 0;
    }

    /**
     * Calculate maximum reservations allowed for a given number of jetskis.
     *
     * @param numJetskis Number of available jetskis of the modelo
     * @return Maximum number of reservations to accept
     */
    public long calcularMaximoReservas(long numJetskis) {
        if (numJetskis <= 0) {
            return 0;
        }

        // Apply overbooking factor
        long calculado = (long) Math.ceil(numJetskis * fatorOverbooking.doubleValue());

        // Apply absolute cap
        return Math.min(calculado, maxReservasSemSinalPorModelo);
    }
}
