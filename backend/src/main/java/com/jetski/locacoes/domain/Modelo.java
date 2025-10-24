package com.jetski.locacoes.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity: Modelo (Jetski Model)
 *
 * Represents a jetski model with pricing configuration, tolerance rules, and additional fees.
 * Each tenant can configure their own models with specific pricing and policies.
 *
 * Examples:
 * - Modelo "Sea-Doo GTI 130" with R$300/hour base price
 * - Modelo "Yamaha VX Cruiser" with R$350/hour and different package prices
 *
 * Business Rules:
 * - RN01: Tolerance (toleranciaMin) defines grace period before billing starts
 * - Pricing packages (pacotesJson) allow custom pricing for time blocks (30min, 60min, 120min)
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Entity
@Table(name = "modelo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Modelo {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String nome;

    private String fabricante;

    @Column(name = "potencia_hp")
    private Integer potenciaHp;

    @Column(name = "capacidade_pessoas")
    @Builder.Default
    private Integer capacidadePessoas = 2;

    @Column(name = "preco_base_hora", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoBaseHora;

    @Column(name = "tolerancia_min")
    @Builder.Default
    private Integer toleranciaMin = 5;

    @Column(name = "taxa_hora_extra", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal taxaHoraExtra = BigDecimal.ZERO;

    @Column(name = "inclui_combustivel")
    @Builder.Default
    private Boolean incluiCombustivel = false;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal caucao = BigDecimal.ZERO;

    @Column(name = "foto_referencia_url")
    private String fotoReferenciaUrl;

    /**
     * Pacotes de precificação por duração (JSONB)
     *
     * Formato esperado:
     * [
     *   {"duracao_min": 30, "preco": 180.00},
     *   {"duracao_min": 60, "preco": 300.00},
     *   {"duracao_min": 120, "preco": 550.00}
     * ]
     */
    @Type(JsonBinaryType.class)
    @Column(name = "pacotes_json", columnDefinition = "jsonb")
    private String pacotesJson;

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
