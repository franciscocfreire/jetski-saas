package com.jetski.combustivel.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fuel_policy", indexes = {
    @Index(name = "idx_fuel_policy_tenant", columnList = "tenant_id"),
    @Index(name = "idx_fuel_policy_aplicacao", columnList = "tenant_id, aplicavel_a, referencia_id"),
    @Index(name = "idx_fuel_policy_ativo", columnList = "tenant_id, ativo")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FuelPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "tipo", nullable = false, length = 20)
    @Convert(converter = FuelChargeModeConverter.class)
    private FuelChargeMode tipo;

    @Column(name = "aplicavel_a", nullable = false, length = 20)
    @Convert(converter = FuelPolicyTypeConverter.class)
    private FuelPolicyType aplicavelA;

    @Column(name = "referencia_id")
    private UUID referenciaId;

    @Column(name = "valor_taxa_por_hora", precision = 10, scale = 2)
    private BigDecimal valorTaxaPorHora;

    @Column(name = "comissionavel", nullable = false)
    @Builder.Default
    private Boolean comissionavel = false;

    @Column(name = "ativo", nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @Column(name = "prioridade", nullable = false)
    @Builder.Default
    private Integer prioridade = 0;

    @Column(name = "descricao", length = 500)
    private String descricao;

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

    public boolean isGlobal() {
        return aplicavelA == FuelPolicyType.GLOBAL;
    }

    public boolean isModelo() {
        return aplicavelA == FuelPolicyType.MODELO;
    }

    public boolean isJetski() {
        return aplicavelA == FuelPolicyType.JETSKI;
    }

    public boolean isTaxaFixa() {
        return tipo == FuelChargeMode.TAXA_FIXA;
    }

    public boolean isMedido() {
        return tipo == FuelChargeMode.MEDIDO;
    }

    public boolean isIncluso() {
        return tipo == FuelChargeMode.INCLUSO;
    }
}
