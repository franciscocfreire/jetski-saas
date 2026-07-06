package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity: lançamento financeiro da reserva (conta/folio da reserva — fase 2).
 *
 * <p>Ledger append-only dos fatos financeiros: nesta fase, o pagamento
 * presencial integral do balcão (dinheiro/PIX/cartão). A fase 3 acrescenta
 * cobranças do check-out e alimenta o fechamento diário por forma de
 * pagamento. {@code reserva.pagamento_status} segue autoritativo — o
 * lançamento é gravado na mesma transação que o confirma.
 */
@Entity
@Table(name = "reserva_lancamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaLancamento {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "reserva_id", nullable = false)
    private UUID reservaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Forma forma;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "registrado_por")
    private UUID registradoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public enum Tipo {
        /** Valor recebido do cliente. */
        PAGAMENTO,
        /** Devolução ao cliente (fase 3 — cancelamento/no-show de reserva paga). */
        ESTORNO
    }

    public enum Forma {
        DINHEIRO,
        PIX,
        CARTAO_CREDITO,
        CARTAO_DEBITO,
        OUTRO
    }
}
