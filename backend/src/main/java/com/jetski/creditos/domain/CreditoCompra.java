package com.jetski.creditos.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Solicitação de compra de créditos via PIX (chave fixa da plataforma).
 * O crédito em si só entra no ledger na aprovação do super admin.
 */
@Entity
@Table(name = "credito_compra")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditoCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "quantidade", nullable = false, updatable = false)
    private Integer quantidade;

    /** Número/identificador da transação PIX (legado — opcional desde a V053). */
    @Column(name = "pix_txid", length = 80, updatable = false)
    private String pixTxid;

    /** Key do comprovante PIX no storage ({tenant}/creditos/compras/{compra}/comprovante.ext). */
    @Column(name = "comprovante_key", length = 255, updatable = false)
    private String comprovanteKey;

    @Column(name = "comprovante_content_type", length = 100, updatable = false)
    private String comprovanteContentType;

    /** SHA-256 do binário — dedupe: o mesmo comprovante não pode ser usado 2x. */
    @Column(name = "comprovante_sha256", length = 64, updatable = false)
    private String comprovanteSha256;

    /** Valor transferido (R$) informado pelo tenant. */
    @Column(name = "valor_pago", precision = 10, scale = 2, updatable = false)
    private java.math.BigDecimal valorPago;

    /** Preço do crédito no momento da solicitação (snapshot). */
    @Column(name = "preco_unitario", precision = 10, scale = 2, updatable = false)
    private java.math.BigDecimal precoUnitario;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusCompra status;

    @Column(name = "criado_por", updatable = false)
    private UUID criadoPor;

    @Column(name = "decidido_por")
    private UUID decididoPor;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    /** Nota do admin (ex.: motivo da rejeição). */
    @Column(name = "observacao", length = 200)
    private String observacao;

    /** Lançamento do ledger gerado na aprovação. */
    @Column(name = "lancamento_id")
    private UUID lancamentoId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = StatusCompra.PENDENTE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
