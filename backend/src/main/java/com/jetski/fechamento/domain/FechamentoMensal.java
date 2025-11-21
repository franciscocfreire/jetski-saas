package com.jetski.fechamento.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity: FechamentoMensal (Monthly Closure)
 *
 * <p>Consolidação financeira mensal com cálculo de resultado líquido e bloqueio retroativo.</p>
 *
 * <p><strong>Regras de Negócio (RN06):</strong></p>
 * <ul>
 *   <li>Quando bloqueado=true, impede edições em locações deste mês</li>
 *   <li>Status: ABERTO → FECHADO → APROVADO</li>
 *   <li>Unique constraint por tenant + ano + mês</li>
 *   <li>resultado_liquido = total_faturado - total_custos - total_comissoes - total_manutencoes</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Entity
@Table(name = "fechamento_mensal",
        uniqueConstraints = {
                @UniqueConstraint(name = "fechamento_mensal_unique",
                        columnNames = {"tenant_id", "ano", "mes"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FechamentoMensal {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ano", nullable = false)
    private Integer ano;

    @Column(name = "mes", nullable = false)
    private Integer mes;

    @Column(name = "operador_id", nullable = false)
    private UUID operadorId;

    // Consolidação
    @Column(name = "total_locacoes", nullable = false)
    @Builder.Default
    private Integer totalLocacoes = 0;

    @Column(name = "total_faturado", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalFaturado = BigDecimal.ZERO;

    @Column(name = "total_custos", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalCustos = BigDecimal.ZERO;

    @Column(name = "total_comissoes", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalComissoes = BigDecimal.ZERO;

    @Column(name = "total_manutencoes", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalManutencoes = BigDecimal.ZERO;

    @Column(name = "resultado_liquido", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal resultadoLiquido = BigDecimal.ZERO;

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

    @Column(name = "relatorio_url", columnDefinition = "TEXT")
    private String relatorioUrl;

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
            throw new IllegalStateException("Fechamento mensal deve estar com status 'fechado' para ser aprovado");
        }
        this.status = "aprovado";
    }

    public void reabrir() {
        if ("aprovado".equals(status)) {
            throw new IllegalStateException("Fechamento mensal aprovado não pode ser reaberto");
        }
        this.status = "aberto";
        this.dtFechamento = null;
        this.bloqueado = false;
    }

    /**
     * Força reabertura mesmo se aprovado (apenas ADMIN_TENANT)
     * Útil para correções administrativas e testes
     */
    public void forcarReabrir() {
        this.status = "aberto";
        this.dtFechamento = null;
        this.bloqueado = false;
    }

    /**
     * Calcula o resultado líquido:
     * resultado_liquido = total_faturado - total_custos - total_comissoes - total_manutencoes
     */
    public void calcularResultadoLiquido() {
        this.resultadoLiquido = totalFaturado
                .subtract(totalCustos != null ? totalCustos : BigDecimal.ZERO)
                .subtract(totalComissoes != null ? totalComissoes : BigDecimal.ZERO)
                .subtract(totalManutencoes != null ? totalManutencoes : BigDecimal.ZERO);
    }

    /**
     * Valida se ano/mês são válidos
     */
    public void validarPeriodo() {
        if (ano == null || ano < 2020) {
            throw new IllegalArgumentException("Ano deve ser >= 2020");
        }
        if (mes == null || mes < 1 || mes > 12) {
            throw new IllegalArgumentException("Mês deve estar entre 1 e 12");
        }
    }
}
