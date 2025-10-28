package com.jetski.combustivel.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "abastecimento", indexes = {
    @Index(name = "idx_abastecimento_tenant", columnList = "tenant_id"),
    @Index(name = "idx_abastecimento_jetski", columnList = "tenant_id, jetski_id"),
    @Index(name = "idx_abastecimento_locacao", columnList = "tenant_id, locacao_id"),
    @Index(name = "idx_abastecimento_data", columnList = "tenant_id, data_hora")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Abastecimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "jetski_id", nullable = false)
    private UUID jetskiId;

    @Column(name = "locacao_id")
    private UUID locacaoId;

    @Column(name = "responsavel_id")
    private UUID responsavelId;

    @Column(name = "data_hora", nullable = false)
    private Instant dataHora;

    @Column(name = "litros", nullable = false, precision = 10, scale = 3)
    private BigDecimal litros;

    @Column(name = "preco_litro", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoLitro;

    @Column(name = "custo_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal custoTotal;

    @Column(name = "tipo", length = 20)
    @Convert(converter = TipoAbastecimentoConverter.class)
    private TipoAbastecimento tipo;

    @Column(name = "foto_id")
    private Long fotoId;

    @Column(name = "observacoes", length = 500)
    private String observacoes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (custoTotal == null && litros != null && precoLitro != null) {
            custoTotal = litros.multiply(precoLitro);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void recalcularCustoTotal() {
        if (litros != null && precoLitro != null) {
            this.custoTotal = litros.multiply(precoLitro);
        }
    }
}
