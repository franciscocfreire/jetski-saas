package com.jetski.reservas.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: pagamento presencial da reserva registrado no balcão.
 *
 * <p>Complementa {@link PagamentoConfirmadoEvent} (publicado na mesma
 * transação) com o fato financeiro: a forma de pagamento presencial
 * (dinheiro/PIX/cartão) que alimenta o ledger {@code reserva_lancamento}.
 * Consumido por {@code AuditEventListener}.
 *
 * @param tenantId      tenant da reserva
 * @param reservaId     reserva paga
 * @param forma         forma de pagamento presencial (DINHEIRO, PIX, ...)
 * @param valor         valor recebido
 * @param observacao    observação livre do staff (opcional)
 * @param registradoPor usuário (staff) que registrou
 * @param occurredAt    quando ocorreu
 */
public record PagamentoPresencialRegistradoEvent(
    UUID tenantId,
    UUID reservaId,
    String forma,
    BigDecimal valor,
    String observacao,
    UUID registradoPor,
    Instant occurredAt
) {
    public static PagamentoPresencialRegistradoEvent of(
            UUID tenantId, UUID reservaId, String forma, BigDecimal valor,
            String observacao, UUID registradoPor) {
        return new PagamentoPresencialRegistradoEvent(
            tenantId, reservaId, forma, valor, observacao, registradoPor, Instant.now());
    }
}
