package com.jetski.creditos.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Linha imutável do ledger de créditos. O banco proíbe UPDATE/DELETE via trigger
 * (append-only); correções são sempre novos lançamentos (AJUSTE/ESTORNO).
 */
@Entity
@Table(name = "credito_lancamento")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditoLancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20, updatable = false)
    private TipoLancamento tipo;

    /** Positivo credita, negativo debita (CONSUMO = -1). */
    @Column(name = "quantidade", nullable = false, updatable = false)
    private Integer quantidade;

    /** Saldo do tenant após este lançamento (evidência de adulteração). */
    @Column(name = "saldo_apos", nullable = false, updatable = false)
    private Integer saldoApos;

    /** documento_emitido.id no CONSUMO. */
    @Column(name = "referencia_id", updatable = false)
    private UUID referenciaId;

    @Column(name = "motivo", length = 200, updatable = false)
    private String motivo;

    @Column(name = "criado_por", updatable = false)
    private UUID criadoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
