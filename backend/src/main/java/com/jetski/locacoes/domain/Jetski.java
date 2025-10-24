package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity: Jetski (Individual Jetski Unit)
 *
 * Represents a specific jetski unit in the fleet, linked to a Modelo.
 * Each jetski has its own serial number, odometer (horimetro), and operational status.
 *
 * Examples:
 * - Jetski #ABC123 (Sea-Doo GTI 130, year 2022, 150.5 hours, available)
 * - Jetski #XYZ789 (Yamaha VX Cruiser, year 2023, 45.2 hours, under maintenance)
 *
 * Business Rules:
 * - RN06: Jetski with status MANUTENCAO cannot be reserved
 * - Only DISPONIVEL jetskis can be reserved and rented
 * - Horimetro (odometer) tracks total operational hours
 * - RN07: Maintenance alerts triggered at specific odometer milestones (50h, 100h, etc.)
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Entity
@Table(name = "jetski")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Jetski {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "modelo_id", nullable = false)
    private UUID modeloId;

    /**
     * Serial number or registration plate
     * Must be unique within the tenant (enforced at database level)
     */
    @Column(nullable = false)
    private String serie;

    @Column
    private Integer ano;

    /**
     * Current odometer reading in hours (decimal for precision)
     * Example: 150.5 means 150 hours and 30 minutes
     */
    @Column(name = "horimetro_atual", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal horimetroAtual = BigDecimal.ZERO;

    /**
     * Operational status of the jetski
     * Controls availability for reservations and rentals
     */
    @Convert(converter = JetskiStatusConverter.class)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JetskiStatus status = JetskiStatus.DISPONIVEL;

    /**
     * Soft delete flag - inactive jetskis are not shown in active lists
     * Use for retired/sold units while preserving rental history
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
     * Check if this jetski can be reserved/rented
     * Business rule RN06: Only DISPONIVEL jetskis can be reserved
     */
    public boolean isDisponivel() {
        return ativo && status == JetskiStatus.DISPONIVEL;
    }

    /**
     * Check if maintenance alert should be triggered
     * Business rule RN07: Alerts at 50h, 100h, 150h milestones
     */
    public boolean requiresMaintenanceAlert() {
        if (horimetroAtual == null) {
            return false;
        }
        int hours = horimetroAtual.intValue();
        // Trigger alert at 50h intervals
        return hours > 0 && hours % 50 == 0;
    }
}
