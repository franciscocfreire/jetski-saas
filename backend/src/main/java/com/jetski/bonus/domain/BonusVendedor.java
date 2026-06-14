package com.jetski.bonus.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity: BonusVendedor (Seller Bonus)
 *
 * Represents a bonus achieved by a seller for reaching sales milestones.
 * The bonus system is cumulative and never resets.
 *
 * Examples:
 * - Seller reaches 50 sales above base price -> Bonus #1 (meta_atingida = 50)
 * - Seller reaches 100 sales above base price -> Bonus #2 (meta_atingida = 100)
 * - Seller reaches 150 sales above base price -> Bonus #3 (meta_atingida = 150)
 *
 * Workflow: PENDENTE -> APROVADO -> PAGO (or CANCELADO)
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Entity
@Table(name = "bonus_vendedor",
        indexes = {
                @Index(name = "idx_bonus_vendedor_tenant", columnList = "tenant_id"),
                @Index(name = "idx_bonus_vendedor_vendedor", columnList = "tenant_id, vendedor_id"),
                @Index(name = "idx_bonus_vendedor_status", columnList = "tenant_id, status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BonusVendedor {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "vendedor_id", nullable = false)
    private UUID vendedorId;

    /**
     * The milestone achieved (cumulative total of sales above base price)
     * E.g., 50, 100, 150 for every 50 sales
     */
    @Column(name = "meta_atingida", nullable = false)
    private Integer metaAtingida;

    /**
     * Bonus value in tenant currency
     */
    @Column(name = "valor_bonus", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorBonus;

    /**
     * Current status of the bonus
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private StatusBonus status = StatusBonus.PENDENTE;

    /**
     * Manager who approved the bonus
     */
    @Column(name = "aprovado_por")
    private UUID aprovadoPor;

    /**
     * Approval timestamp
     */
    @Column(name = "aprovado_em")
    private Instant aprovadoEm;

    /**
     * Finance user who processed the payment
     */
    @Column(name = "pago_por")
    private UUID pagoPor;

    /**
     * Payment timestamp
     */
    @Column(name = "pago_em")
    private Instant pagoEm;

    /**
     * Payment reference (e.g., PIX-2024-001)
     */
    @Column(name = "referencia_pagamento", length = 100)
    private String referenciaPagamento;

    /**
     * Link to bulk payment record (when paid via PagamentoVendedor)
     */
    @Column(name = "pagamento_id")
    private UUID pagamentoId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) {
            status = StatusBonus.PENDENTE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Check if bonus can be approved
     */
    public boolean podeAprovar() {
        return status == StatusBonus.PENDENTE;
    }

    /**
     * Check if bonus can be paid
     */
    public boolean podePagar() {
        return status == StatusBonus.APROVADO;
    }
}
