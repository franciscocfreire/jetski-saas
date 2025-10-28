package com.jetski.combustivel.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fuel_price_day",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_fuel_price_day", columnNames = {"tenant_id", "data"})
    },
    indexes = {
        @Index(name = "idx_fuel_price_tenant_data", columnList = "tenant_id, data")
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FuelPriceDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Column(name = "preco_medio_litro", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoMedioLitro;

    @Column(name = "total_litros_abastecidos", precision = 10, scale = 3)
    private BigDecimal totalLitrosAbastecidos;

    @Column(name = "total_custo", precision = 10, scale = 2)
    private BigDecimal totalCusto;

    @Column(name = "qtd_abastecimentos")
    private Integer qtdAbastecimentos;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
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

    public void recalcularPrecoMedio() {
        if (totalLitrosAbastecidos != null && totalLitrosAbastecidos.compareTo(BigDecimal.ZERO) > 0) {
            this.precoMedioLitro = totalCusto.divide(totalLitrosAbastecidos, 2, RoundingMode.HALF_UP);
        }
    }
}
