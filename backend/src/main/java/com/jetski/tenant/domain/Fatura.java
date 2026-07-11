package com.jetski.tenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fatura mensal da assinatura (billing manual assistido, V045).
 *
 * <p>Gerada pelo {@code FaturamentoJob} para tenants com plano PAGO; paga via
 * PIX da plataforma com conferência humana do super admin (mesmo fluxo da
 * compra de créditos). Única por (tenant, competência).
 */
@Entity
@Table(name = "fatura")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fatura {

    public enum Status { ABERTA, EM_CONFERENCIA, PAGA, CANCELADA }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** 1º dia do mês de referência. */
    @Column(name = "competencia", nullable = false)
    private LocalDate competencia;

    @Column(name = "plano_nome", nullable = false, length = 60)
    private String planoNome;

    @Column(name = "valor", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ABERTA;

    @Column(name = "vencimento", nullable = false)
    private LocalDate vencimento;

    @Column(name = "pix_copia_e_cola")
    private String pixCopiaECola;

    /** Nº da transação PIX informado pela empresa (conferência no extrato). */
    @Column(name = "txid_informado", length = 80)
    private String txidInformado;

    @Column(name = "informado_em")
    private Instant informadoEm;

    @Column(name = "pago_em")
    private Instant pagoEm;

    /** Super admin que confirmou/cancelou. */
    @Column(name = "decidido_por")
    private UUID decididoPor;

    @Column(name = "observacao", length = 300)
    private String observacao;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
