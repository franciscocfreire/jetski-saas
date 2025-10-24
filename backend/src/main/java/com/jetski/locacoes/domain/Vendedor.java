package com.jetski.locacoes.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: Vendedor (Seller/Partner)
 *
 * Represents a seller or sales partner who originates reservations and rentals.
 * Sellers earn commissions based on configurable rules (RF08, RN04).
 *
 * Types:
 * - INTERNO: Internal employees (pier operators, managers)
 * - PARCEIRO: External partners (agencies, affiliates, sales reps)
 *
 * Examples:
 * - João Silva (INTERNO) - 10% base commission
 * - Agência Praia Sol (PARCEIRO) - 15% base commission with campaign bonuses
 *
 * Business Rules:
 * - RF08: Commission calculation hierarchy (campaign > model > duration > seller default)
 * - RN04: Commission calculated on commissionable revenue (excludes fuel, fees, fines)
 * - Commission rules stored in regraComissaoJson for flexibility
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Entity
@Table(name = "vendedor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vendedor {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String nome;

    /**
     * CPF (individual) or CNPJ (legal entity)
     * Optional - some partners may not provide tax ID
     */
    @Column
    private String documento;

    /**
     * Seller type: INTERNO (employee) or PARCEIRO (external partner)
     */
    @Convert(converter = VendedorTipoConverter.class)
    @Column(nullable = false, length = 20)
    private VendedorTipo tipo;

    /**
     * Commission rules (JSONB) - Default rules for this seller
     *
     * Formato esperado:
     * {
     *   "percentual_padrao": 10.0,
     *   "por_modelo": [
     *     {"modelo_id": 1, "percentual": 12.0},
     *     {"modelo_id": 2, "percentual": 8.0}
     *   ],
     *   "escalonada": [
     *     {"ate_min": 120, "percentual": 10.0},
     *     {"acima_min": 120, "percentual": 12.0}
     *   ]
     * }
     *
     * Note: These are DEFAULT rules. Actual commission may be overridden by:
     * 1. Active campaign rules
     * 2. Per-model rules
     * 3. Duration-based rules
     */
    @Type(JsonBinaryType.class)
    @Column(name = "regra_comissao_json", columnDefinition = "jsonb")
    private String regraComissaoJson;

    /**
     * Soft delete flag - inactive sellers cannot create new rentals
     * but historical rentals and commissions are preserved
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
}
