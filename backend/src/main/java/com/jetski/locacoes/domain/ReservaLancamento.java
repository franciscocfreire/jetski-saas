package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity: lançamento do folio financeiro (fase 3 — apesar do nome, a tabela
 * é o folio GERAL: lançamentos pendurados em reserva E/OU locação; walk-in
 * não tem reserva).
 *
 * <p>Ledger append-only: PAGAMENTO/ESTORNO são fatos de caixa (forma
 * obrigatória); COBRANCA_* são derivadas do sistema no check-out (sem forma)
 * e podem ser relançadas quando a locação finalizada é editada.
 * {@code reserva.pagamento_status} segue autoritativo para a reserva.
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

    /** Âncora opcional — obrigatória quando não há locação (CHECK no banco). */
    @Column(name = "reserva_id")
    private UUID reservaId;

    /** Âncora opcional — cobranças/recebimentos do check-out (walk-in só tem esta). */
    @Column(name = "locacao_id")
    private UUID locacaoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    /** Obrigatória para PAGAMENTO/ESTORNO; nula para COBRANCA_* (CHECK no banco). */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
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
        /** Valor recebido do cliente (fato de caixa; forma obrigatória). */
        PAGAMENTO,
        /** Devolução ao cliente — cancelamento/no-show de reserva paga (fato de caixa). */
        ESTORNO,
        /** Débito do aluguel apurado no check-out (derivada; sem forma). */
        COBRANCA_ALUGUEL,
        /** Débito do combustível (RN03) apurado no check-out (derivada; sem forma). */
        COBRANCA_COMBUSTIVEL,
        /** Débito dos itens opcionais apurados no check-out (derivada; sem forma). */
        COBRANCA_EXTRAS;

        /** Cobranças são derivadas do sistema e relançáveis; caixa nunca é tocado. */
        public boolean isCobranca() {
            return this == COBRANCA_ALUGUEL || this == COBRANCA_COMBUSTIVEL || this == COBRANCA_EXTRAS;
        }
    }

    public enum Forma {
        DINHEIRO,
        PIX,
        CARTAO_CREDITO,
        CARTAO_DEBITO,
        OUTRO
    }
}
