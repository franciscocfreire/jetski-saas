package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity: ItemOpcional (Optional Add-on Item)
 *
 * Represents configurable optional items/services that can be added to a rental.
 * Each tenant manages their own catalog of optional items.
 *
 * Examples:
 * - Drone video recording
 * - Action cam recording
 * - Life jacket rental
 * - GPS tracker
 * - Cooler with drinks
 *
 * Business Rules:
 * - Each item has a base price (precoBase)
 * - Price can be negotiated when adding to a rental
 * - Items are tenant-scoped (each company has its own catalog)
 * - Inactive items cannot be added to new rentals
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Entity
@Table(name = "item_opcional")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemOpcional {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Item name (e.g., "Gravação Drone", "Action Cam")
     */
    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    /**
     * Item description
     */
    @Column(name = "descricao", length = 500)
    private String descricao;

    /**
     * Base price for this optional item
     */
    @Column(name = "preco_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoBase;

    /**
     * Whether this item is active and can be added to rentals
     */
    @Column(name = "ativo", nullable = false)
    @Builder.Default
    private Boolean ativo = true;

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
}
