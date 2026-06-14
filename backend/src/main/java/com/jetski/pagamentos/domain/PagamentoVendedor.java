package com.jetski.pagamentos.domain;

import com.jetski.locacoes.domain.TipoChavePix;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity: PagamentoVendedor (Seller Payment Record)
 *
 * Registro de pagamento em lote para vendedores.
 * Consolida comissões aprovadas e diárias pendentes em um único pagamento.
 *
 * <p>Cada registro representa uma transferência PIX realizada para o vendedor,
 * incluindo:</p>
 * <ul>
 *   <li>Valor total de comissões pagas</li>
 *   <li>Valor total de diárias pagas</li>
 *   <li>Snapshot da chave PIX usada</li>
 *   <li>Referência do pagamento (ID da transação)</li>
 *   <li>URL do comprovante (se uploadado)</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.12.0
 */
@Entity
@Table(name = "pagamento_vendedor",
        indexes = {
                @Index(name = "idx_pagamento_vendedor_tenant", columnList = "tenant_id"),
                @Index(name = "idx_pagamento_vendedor_vendedor", columnList = "tenant_id, vendedor_id"),
                @Index(name = "idx_pagamento_vendedor_periodo", columnList = "tenant_id, periodo_inicio, periodo_fim")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagamentoVendedor {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "vendedor_id", nullable = false)
    private UUID vendedorId;

    /**
     * Nome do vendedor (snapshot no momento do pagamento)
     */
    @Column(name = "vendedor_nome", nullable = false)
    private String vendedorNome;

    // ========== Payment Values ==========

    /**
     * Total de comissões incluídas neste pagamento
     */
    @Column(name = "valor_comissoes", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorComissoes = BigDecimal.ZERO;

    /**
     * Total de diárias incluídas neste pagamento
     */
    @Column(name = "valor_diarias", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorDiarias = BigDecimal.ZERO;

    /**
     * Total de bônus incluídos neste pagamento
     */
    @Column(name = "valor_bonus", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorBonus = BigDecimal.ZERO;

    /**
     * Valor total do pagamento (comissões + diárias + bônus)
     */
    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    // ========== Payment Type ==========

    /**
     * Tipo de pagamento (PIX ou DINHEIRO)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pagamento", nullable = false, length = 20)
    @Builder.Default
    private TipoPagamento tipoPagamento = TipoPagamento.PIX;

    // ========== PIX Snapshot ==========

    /**
     * Chave PIX usada no momento do pagamento (snapshot)
     */
    @Column(name = "chave_pix", length = 100)
    private String chavePix;

    /**
     * Tipo da chave PIX (snapshot)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_chave_pix", length = 20)
    private TipoChavePix tipoChavePix;

    // ========== Payment Reference ==========

    /**
     * Referência do pagamento (ID da transação PIX, E2E, etc.)
     */
    @Column(name = "referencia_pagamento", length = 100)
    private String referenciaPagamento;

    /**
     * URL pública do comprovante (S3 presigned ou CloudFront)
     */
    @Column(name = "comprovante_url", length = 500)
    private String comprovanteUrl;

    /**
     * Chave S3 do comprovante para download direto
     */
    @Column(name = "comprovante_s3_key", length = 255)
    private String comprovanteS3Key;

    // ========== Quantities ==========

    /**
     * Quantidade de comissões incluídas neste pagamento
     */
    @Column(name = "qtd_comissoes", nullable = false)
    @Builder.Default
    private Integer qtdComissoes = 0;

    /**
     * Quantidade de diárias incluídas neste pagamento
     */
    @Column(name = "qtd_diarias", nullable = false)
    @Builder.Default
    private Integer qtdDiarias = 0;

    /**
     * Quantidade de bônus incluídos neste pagamento
     */
    @Column(name = "qtd_bonus", nullable = false)
    @Builder.Default
    private Integer qtdBonus = 0;

    // ========== Period ==========

    /**
     * Data de início do período coberto
     */
    @Column(name = "periodo_inicio")
    private LocalDate periodoInicio;

    /**
     * Data de fim do período coberto
     */
    @Column(name = "periodo_fim")
    private LocalDate periodoFim;

    // ========== Audit ==========

    /**
     * Usuário que realizou o pagamento
     */
    @Column(name = "pago_por", nullable = false)
    private UUID pagoPor;

    /**
     * Observações adicionais sobre o pagamento
     */
    @Column(name = "observacoes", length = 500)
    private String observacoes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        // Calculate total if not set
        if (valorTotal == null) {
            valorTotal = (valorComissoes != null ? valorComissoes : BigDecimal.ZERO)
                    .add(valorDiarias != null ? valorDiarias : BigDecimal.ZERO)
                    .add(valorBonus != null ? valorBonus : BigDecimal.ZERO);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
