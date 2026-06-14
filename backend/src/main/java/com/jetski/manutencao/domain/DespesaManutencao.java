package com.jetski.manutencao.domain;

import com.jetski.despesas.domain.StatusDespesa;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity: DespesaManutencao (Maintenance Expense)
 *
 * <p>Despesas originadas de ordens de servico de manutencao, com suporte a parcelamento.</p>
 *
 * <p><strong>Fluxo de status:</strong></p>
 * <ol>
 *   <li>PENDENTE - Despesa gerada, aguardando aprovacao</li>
 *   <li>APROVADA - Gerente aprovou para pagamento</li>
 *   <li>PAGA - Financeiro registrou o pagamento</li>
 *   <li>REJEITADA - Despesa rejeitada (opcional)</li>
 *   <li>CANCELADA - Despesa cancelada</li>
 * </ol>
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Entity
@Table(name = "despesa_manutencao",
        indexes = {
                @Index(name = "idx_despesa_manutencao_tenant", columnList = "tenant_id"),
                @Index(name = "idx_despesa_manutencao_os", columnList = "os_manutencao_id"),
                @Index(name = "idx_despesa_manutencao_vencimento", columnList = "tenant_id, dt_vencimento"),
                @Index(name = "idx_despesa_manutencao_status", columnList = "tenant_id, status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DespesaManutencao {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Referencia a ordem de servico de manutencao origem
     */
    @Column(name = "os_manutencao_id", nullable = false)
    private UUID osManutencaoId;

    /**
     * Data de vencimento desta parcela
     */
    @Column(name = "dt_vencimento", nullable = false)
    private LocalDate dtVencimento;

    /**
     * Numero da parcela (1, 2, 3...)
     */
    @Column(name = "numero_parcela", nullable = false)
    @Builder.Default
    private Integer numeroParcela = 1;

    /**
     * Total de parcelas
     */
    @Column(name = "total_parcelas", nullable = false)
    @Builder.Default
    private Integer totalParcelas = 1;

    /**
     * Valor desta parcela (sempre positivo)
     */
    @Column(name = "valor", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    /**
     * Status da despesa no workflow
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private StatusDespesa status = StatusDespesa.PENDENTE;

    // ========== Aprovacao ==========

    /**
     * Usuario que aprovou a despesa (GERENTE) - ID do membro
     */
    @Column(name = "aprovado_por")
    private Integer aprovadoPor;

    /**
     * Data/hora de aprovacao
     */
    @Column(name = "aprovado_em")
    private Instant aprovadoEm;

    // ========== Pagamento ==========

    /**
     * Usuario que registrou o pagamento (FINANCEIRO) - ID do membro
     */
    @Column(name = "pago_por")
    private Integer pagoPor;

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
        if (numeroParcela == null) {
            numeroParcela = 1;
        }
        if (totalParcelas == null) {
            totalParcelas = 1;
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
     * Verifica se a despesa pode ser cancelada
     */
    public boolean podeCancelar() {
        return status == StatusDespesa.PENDENTE || status == StatusDespesa.APROVADA;
    }

    /**
     * Aprova a despesa
     */
    public void aprovar(Integer membroId) {
        if (!podeAprovar()) {
            throw new IllegalStateException("Despesa de manutencao nao pode ser aprovada no status atual: " + status);
        }
        this.status = StatusDespesa.APROVADA;
        this.aprovadoPor = membroId;
        this.aprovadoEm = Instant.now();
    }

    /**
     * Rejeita a despesa
     */
    public void rejeitar(Integer membroId, String motivo) {
        if (!podeRejeitar()) {
            throw new IllegalStateException("Despesa de manutencao nao pode ser rejeitada no status atual: " + status);
        }
        this.status = StatusDespesa.REJEITADA;
        this.aprovadoPor = membroId;
        this.aprovadoEm = Instant.now();
        if (motivo != null && !motivo.isBlank()) {
            this.observacoes = (this.observacoes != null ? this.observacoes + " | " : "") + "Rejeitada: " + motivo;
        }
    }

    /**
     * Marca a despesa como paga
     */
    public void marcarComoPaga(Integer membroId, String referencia) {
        if (!podePagar()) {
            throw new IllegalStateException("Despesa de manutencao nao pode ser paga no status atual: " + status);
        }
        this.status = StatusDespesa.PAGA;
        this.pagoPor = membroId;
        this.pagoEm = Instant.now();
        this.referenciaPagamento = referencia;
    }

    /**
     * Cancela a despesa
     */
    public void cancelar(Integer membroId, String motivo) {
        if (!podeCancelar()) {
            throw new IllegalStateException("Despesa de manutencao nao pode ser cancelada no status atual: " + status);
        }
        this.status = StatusDespesa.CANCELADA;
        if (motivo != null && !motivo.isBlank()) {
            this.observacoes = (this.observacoes != null ? this.observacoes + " | " : "") + "Cancelada: " + motivo;
        }
    }

    /**
     * Retorna descricao formatada da parcela
     */
    public String getDescricaoParcela() {
        if (totalParcelas == 1) {
            return "Pagamento unico";
        }
        return String.format("Parcela %d/%d", numeroParcela, totalParcelas);
    }
}
