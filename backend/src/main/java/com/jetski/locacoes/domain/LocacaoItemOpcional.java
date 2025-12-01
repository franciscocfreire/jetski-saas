package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity: LocacaoItemOpcional (Rental Optional Item)
 *
 * Represents an optional item attached to a specific rental.
 * Tracks the price charged (which may differ from the catalog base price).
 *
 * Business Rules:
 * - Items can be added at any time (check-in, during rental, or after check-out)
 * - valorCobrado may differ from valorOriginal (negotiation allowed)
 * - Sum of all items contributes to locacao.valorTotal
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Entity
@Table(name = "locacao_item_opcional")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocacaoItemOpcional {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Reference to the rental
     */
    @Column(name = "locacao_id", nullable = false)
    private UUID locacaoId;

    /**
     * Reference to the optional item from catalog
     */
    @Column(name = "item_opcional_id", nullable = false)
    private UUID itemOpcionalId;

    /**
     * Actual price charged (may differ from base price if negotiated)
     */
    @Column(name = "valor_cobrado", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorCobrado;

    /**
     * Original catalog price at the time of addition (for reference/audit)
     */
    @Column(name = "valor_original", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorOriginal;

    /**
     * Optional note explaining price adjustment
     */
    @Column(name = "observacao", length = 255)
    private String observacao;

    // ===================================================================
    // Audit Fields
    // ===================================================================

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // ===================================================================
    // Business Logic Helpers
    // ===================================================================

    /**
     * Check if the price was negotiated (different from original)
     */
    public boolean isNegociado() {
        return valorCobrado != null && valorOriginal != null
            && valorCobrado.compareTo(valorOriginal) != 0;
    }

    /**
     * Calculate discount amount (positive = discount, negative = surcharge)
     */
    public BigDecimal getDescontoAplicado() {
        if (valorOriginal == null || valorCobrado == null) {
            return BigDecimal.ZERO;
        }
        return valorOriginal.subtract(valorCobrado);
    }
}
