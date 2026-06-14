package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity: PresencaVendedor (Seller Daily Attendance)
 *
 * Registro de presença diária dos vendedores para cálculo de diárias.
 *
 * <p>Tipos de presença:</p>
 * <ul>
 *   <li>INTEGRAL - Diária completa (100%)</li>
 *   <li>MEIA_DIARIA - Meia diária (50%) - dias com chuva, término antecipado</li>
 * </ul>
 *
 * <p>O valor efetivo da diária é calculado como:</p>
 * <code>valorEfetivo = valorAjustado != null ? valorAjustado : valorDiaria</code>
 *
 * @author Jetski Team
 * @since 0.10.0
 */
@Entity
@Table(name = "presenca_vendedor",
        uniqueConstraints = {
                @UniqueConstraint(name = "presenca_unique",
                        columnNames = {"tenant_id", "vendedor_id", "dt_referencia"})
        },
        indexes = {
                @Index(name = "idx_presenca_vendedor_tenant", columnList = "tenant_id"),
                @Index(name = "idx_presenca_vendedor_data", columnList = "tenant_id, dt_referencia"),
                @Index(name = "idx_presenca_vendedor_vendedor", columnList = "tenant_id, vendedor_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PresencaVendedor {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "vendedor_id", nullable = false, insertable = false, updatable = false)
    private UUID vendedorId;

    /**
     * Relacionamento com Vendedor para acesso aos dados do vendedor.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id", nullable = false)
    private Vendedor vendedor;

    /**
     * Data de referência da presença
     */
    @Column(name = "dt_referencia", nullable = false)
    private LocalDate dtReferencia;

    /**
     * Tipo de presença: INTEGRAL ou MEIA_DIARIA
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TipoPresenca tipo = TipoPresenca.INTEGRAL;

    /**
     * Valor calculado da diária: vendedor.diariaBase * tipo.fator
     */
    @Column(name = "valor_diaria", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorDiaria;

    /**
     * Valor ajustado manualmente (opcional).
     * Se preenchido, substitui o valorDiaria calculado.
     */
    @Column(name = "valor_ajustado", precision = 10, scale = 2)
    private BigDecimal valorAjustado;

    /**
     * Motivo do ajuste (obrigatório se valorAjustado != null)
     */
    @Column(name = "motivo_ajuste", length = 255)
    private String motivoAjuste;

    /**
     * Usuário que registrou a presença
     */
    @Column(name = "registrado_por")
    private UUID registradoPor;

    // ========== Payment Tracking Fields ==========

    /**
     * ID do pagamento em lote que incluiu esta diária.
     * Preenchido quando a diária é paga.
     */
    @Column(name = "pagamento_id")
    private UUID pagamentoId;

    /**
     * Data/hora em que a diária foi paga
     */
    @Column(name = "pago_em")
    private Instant pagoEm;

    /**
     * Usuário que realizou o pagamento
     */
    @Column(name = "pago_por")
    private UUID pagoPor;

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

    // ========== Business Methods ==========

    /**
     * Retorna o valor efetivo da diária.
     * Se houver valor ajustado, usa ele; senão usa o valor calculado.
     *
     * @return valor efetivo da diária
     */
    public BigDecimal getValorEfetivo() {
        return valorAjustado != null ? valorAjustado : valorDiaria;
    }

    /**
     * Verifica se a diária foi ajustada manualmente.
     */
    public boolean isAjustado() {
        return valorAjustado != null;
    }

    /**
     * Verifica se a diária já foi paga.
     */
    public boolean isPaga() {
        return pagoEm != null;
    }
}
