package com.jetski.comissoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity: Comissao (Commission Calculation)
 *
 * <p>Registro de comissão calculada para uma locação específica.</p>
 *
 * <p><strong>Fluxo de status:</strong></p>
 * <ol>
 *   <li>PENDENTE - Calculada automaticamente após check-out</li>
 *   <li>APROVADA - Gerente aprova para pagamento</li>
 *   <li>PAGA - Financeiro marca como paga</li>
 *   <li>CANCELADA - Locação cancelada/estornada</li>
 * </ol>
 *
 * <p><strong>Receita comissionável (RN04):</strong></p>
 * <ul>
 *   <li>Incluído: valor_locacao, extras</li>
 *   <li>Excluído: combustível, multas, taxas de limpeza, danos</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Entity
@Table(name = "comissao",
        indexes = {
                @Index(name = "idx_comissao_tenant_vendedor", columnList = "tenant_id, vendedor_id"),
                @Index(name = "idx_comissao_tenant_locacao", columnList = "tenant_id, locacao_id"),
                @Index(name = "idx_comissao_tenant_status", columnList = "tenant_id, status"),
                @Index(name = "idx_comissao_tenant_data", columnList = "tenant_id, data_locacao")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comissao {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Locação que gerou a comissão
     */
    @Column(name = "locacao_id", nullable = false)
    private UUID locacaoId;

    /**
     * Vendedor/parceiro que receberá a comissão
     */
    @Column(name = "vendedor_id", nullable = false)
    private UUID vendedorId;

    /**
     * Política de comissão aplicada (para rastreabilidade)
     */
    @Column(name = "politica_id")
    private UUID politicaId;

    /**
     * Status da comissão
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private StatusComissao status = StatusComissao.PENDENTE;

    /**
     * Data da locação (para agrupamento mensal)
     */
    @Column(name = "data_locacao", nullable = false)
    private Instant dataLocacao;

    // ========== Valores de cálculo ==========

    /**
     * Valor total da locação (antes de exclusões)
     */
    @Column(name = "valor_total_locacao", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotalLocacao;

    /**
     * Valor de combustível (não-comissionável)
     */
    @Column(name = "valor_combustivel", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorCombustivel = BigDecimal.ZERO;

    /**
     * Valor de multas/danos (não-comissionável)
     */
    @Column(name = "valor_multas", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorMultas = BigDecimal.ZERO;

    /**
     * Outras taxas não-comissionáveis (limpeza, etc)
     */
    @Column(name = "valor_taxas", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorTaxas = BigDecimal.ZERO;

    /**
     * Valor comissionável = valor_total - combustível - multas - taxas
     */
    @Column(name = "valor_comissionavel", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorComissionavel;

    /**
     * Tipo de comissão aplicado
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_comissao", nullable = false, length = 20)
    private TipoComissao tipoComissao;

    /**
     * Percentual aplicado (se PERCENTUAL ou ESCALONADO)
     */
    @Column(name = "percentual_aplicado", precision = 5, scale = 2)
    private BigDecimal percentualAplicado;

    /**
     * Valor da comissão calculado
     */
    @Column(name = "valor_comissao", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorComissao;

    // ========== Detalhes para rastreabilidade ==========

    /**
     * Nome da política aplicada (snapshot para histórico)
     */
    @Column(name = "politica_nome", length = 100)
    private String politicaNome;

    /**
     * Nível hierárquico da política aplicada
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "politica_nivel", length = 20)
    private NivelPolitica politicaNivel;

    /**
     * Observações sobre o cálculo
     */
    @Column(name = "observacoes", length = 500)
    private String observacoes;

    // ========== Aprovação e pagamento ==========

    /**
     * Usuário que aprovou a comissão (GERENTE)
     */
    @Column(name = "aprovado_por")
    private UUID aprovadoPor;

    /**
     * Data/hora de aprovação
     */
    @Column(name = "aprovado_em")
    private Instant aprovadoEm;

    /**
     * Usuário que registrou o pagamento (FINANCEIRO)
     */
    @Column(name = "pago_por")
    private UUID pagoPor;

    /**
     * Data/hora de pagamento
     */
    @Column(name = "pago_em")
    private Instant pagoEm;

    /**
     * Referência do pagamento (transferência bancária, etc)
     */
    @Column(name = "referencia_pagamento", length = 100)
    private String referenciaPagamento;

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
            status = StatusComissao.PENDENTE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Calcula o valor comissionável (valor total - exclusões)
     */
    public void calcularValorComissionavel() {
        this.valorComissionavel = valorTotalLocacao
                .subtract(valorCombustivel != null ? valorCombustivel : BigDecimal.ZERO)
                .subtract(valorMultas != null ? valorMultas : BigDecimal.ZERO)
                .subtract(valorTaxas != null ? valorTaxas : BigDecimal.ZERO);
    }

    /**
     * Verifica se pode ser aprovada
     */
    public boolean podeAprovar() {
        return status == StatusComissao.PENDENTE;
    }

    /**
     * Verifica se pode ser paga
     */
    public boolean podePagar() {
        return status == StatusComissao.APROVADA;
    }
}
