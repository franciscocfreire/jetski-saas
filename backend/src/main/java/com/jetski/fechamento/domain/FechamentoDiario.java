package com.jetski.fechamento.domain;

import com.jetski.shared.exception.BusinessException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity: FechamentoDiario (Daily Closure)
 *
 * <p>Consolidação financeira diária com bloqueio de edições retroativas.</p>
 *
 * <p><strong>Regras de Negócio (RN06):</strong></p>
 * <ul>
 *   <li>Quando bloqueado=true, impede edições em locações desta data</li>
 *   <li>Status: ABERTO → FECHADO → APROVADO</li>
 *   <li>Unique constraint por tenant + data de referência</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Entity
@Table(name = "fechamento_diario",
        uniqueConstraints = {
                @UniqueConstraint(name = "fechamento_diario_unique",
                        columnNames = {"tenant_id", "dt_referencia"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FechamentoDiario {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dt_referencia", nullable = false)
    private LocalDate dtReferencia;

    @Column(name = "operador_id", nullable = false)
    private UUID operadorId;

    // Consolidação
    @Column(name = "total_locacoes", nullable = false)
    @Builder.Default
    private Integer totalLocacoes = 0;

    @Column(name = "total_faturado", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalFaturado = BigDecimal.ZERO;

    @Column(name = "total_combustivel", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalCombustivel = BigDecimal.ZERO;

    @Column(name = "total_comissoes", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalComissoes = BigDecimal.ZERO;

    @Column(name = "total_dinheiro", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalDinheiro = BigDecimal.ZERO;

    @Column(name = "total_cartao", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalCartao = BigDecimal.ZERO;

    @Column(name = "total_pix", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalPix = BigDecimal.ZERO;

    // Status & Lock
    @Column(name = "status", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String status = "aberto";

    @Column(name = "dt_fechamento")
    private Instant dtFechamento;

    @Column(name = "bloqueado", nullable = false)
    @Builder.Default
    private Boolean bloqueado = false;

    // Metadata
    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "divergencias_json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String divergenciasJson;

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

    // Business methods
    public boolean isFechado() {
        return "fechado".equals(status) || "aprovado".equals(status);
    }

    public boolean podeEditar() {
        return !bloqueado && "aberto".equals(status);
    }

    public void fechar() {
        this.status = "fechado";
        this.dtFechamento = Instant.now();
        this.bloqueado = true;
    }

    public void aprovar() {
        if (!"fechado".equals(status)) {
            throw new BusinessException("Fechamento deve estar com status 'fechado' para ser aprovado");
        }
        this.status = "aprovado";
    }

    public void reabrir() {
        if ("aprovado".equals(status)) {
            throw new BusinessException("Fechamento aprovado não pode ser reaberto");
        }
        this.status = "aberto";
        this.dtFechamento = null;
        this.bloqueado = false;
    }
}
