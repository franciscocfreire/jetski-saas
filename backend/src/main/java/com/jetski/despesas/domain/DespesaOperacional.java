package com.jetski.despesas.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity: DespesaOperacional (Operational Expense)
 *
 * <p>Despesas do dia a dia nao vinculadas a locacoes especificas.
 * Ex: diarias de funcionarios, refeicao, combustivel proprio, limpeza, etc.</p>
 *
 * <p><strong>Fluxo de status:</strong></p>
 * <ol>
 *   <li>PENDENTE - Registrada pelo operador/gerente</li>
 *   <li>APROVADA - Gerente aprova para pagamento</li>
 *   <li>PAGA - Financeiro marca como paga</li>
 *   <li>REJEITADA - Despesa rejeitada (opcional)</li>
 * </ol>
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Entity
@Table(name = "despesa_operacional",
        indexes = {
                @Index(name = "idx_despesa_operacional_tenant", columnList = "tenant_id"),
                @Index(name = "idx_despesa_operacional_data", columnList = "tenant_id, dt_referencia"),
                @Index(name = "idx_despesa_operacional_categoria", columnList = "tenant_id, categoria"),
                @Index(name = "idx_despesa_operacional_status", columnList = "tenant_id, status"),
                @Index(name = "idx_despesa_operacional_responsavel", columnList = "tenant_id, responsavel_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DespesaOperacional {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Data de referencia da despesa
     */
    @Column(name = "dt_referencia", nullable = false)
    private LocalDate dtReferencia;

    /**
     * Categoria da despesa
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 30)
    private CategoriaDespesa categoria;

    /**
     * Descricao detalhada da despesa
     */
    @Column(name = "descricao", length = 255)
    private String descricao;

    /**
     * Valor da despesa (sempre positivo)
     */
    @Column(name = "valor", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    /**
     * Usuario responsavel pela despesa (quem gastou)
     */
    @Column(name = "responsavel_id")
    private UUID responsavelId;

    /**
     * Status da despesa no workflow
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private StatusDespesa status = StatusDespesa.PENDENTE;

    // ========== Aprovacao ==========

    /**
     * Usuario que aprovou a despesa (GERENTE)
     */
    @Column(name = "aprovado_por")
    private UUID aprovadoPor;

    /**
     * Data/hora de aprovacao
     */
    @Column(name = "aprovado_em")
    private Instant aprovadoEm;

    // ========== Pagamento ==========

    /**
     * Usuario que registrou o pagamento (FINANCEIRO)
     */
    @Column(name = "pago_por")
    private UUID pagoPor;

    /**
     * Data/hora de pagamento
     */
    @Column(name = "pago_em")
    private Instant pagoEm;

    /**
     * Referencia do pagamento (transferencia, recibo, etc)
     */
    @Column(name = "referencia_pagamento", length = 100)
    private String referenciaPagamento;

    // ========== Observacoes ==========

    /**
     * Observacoes adicionais
     */
    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    // ========== Auditoria ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) {
            status = StatusDespesa.PENDENTE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ========== Metodos de negocio ==========

    /**
     * Verifica se a despesa pode ser aprovada
     */
    public boolean podeAprovar() {
        return status == StatusDespesa.PENDENTE;
    }

    /**
     * Verifica se a despesa pode ser rejeitada
     */
    public boolean podeRejeitar() {
        return status == StatusDespesa.PENDENTE;
    }

    /**
     * Verifica se a despesa pode ser marcada como paga
     */
    public boolean podePagar() {
        return status == StatusDespesa.APROVADA;
    }

    /**
     * Verifica se a despesa pode ser editada
     */
    public boolean podeEditar() {
        return status == StatusDespesa.PENDENTE;
    }

    /**
     * Aprova a despesa
     */
    public void aprovar(UUID aprovadorId) {
        if (!podeAprovar()) {
            throw new IllegalStateException("Despesa nao pode ser aprovada no status atual: " + status);
        }
        this.status = StatusDespesa.APROVADA;
        this.aprovadoPor = aprovadorId;
        this.aprovadoEm = Instant.now();
    }

    /**
     * Rejeita a despesa
     */
    public void rejeitar(UUID aprovadorId, String motivo) {
        if (!podeRejeitar()) {
            throw new IllegalStateException("Despesa nao pode ser rejeitada no status atual: " + status);
        }
        this.status = StatusDespesa.REJEITADA;
        this.aprovadoPor = aprovadorId;
        this.aprovadoEm = Instant.now();
        if (motivo != null && !motivo.isBlank()) {
            this.observacoes = (this.observacoes != null ? this.observacoes + " | " : "") + "Rejeitada: " + motivo;
        }
    }

    /**
     * Marca a despesa como paga
     */
    public void marcarComoPaga(UUID pagadorId, String referencia) {
        if (!podePagar()) {
            throw new IllegalStateException("Despesa nao pode ser paga no status atual: " + status);
        }
        this.status = StatusDespesa.PAGA;
        this.pagoPor = pagadorId;
        this.pagoEm = Instant.now();
        this.referenciaPagamento = referencia;
    }
}
