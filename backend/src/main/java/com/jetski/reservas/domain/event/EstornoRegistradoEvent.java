package com.jetski.reservas.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: estorno (devolução ao cliente) registrado para uma reserva
 * paga — cancelamento/no-show. Fato de caixa manual, registrado quando a
 * loja devolve o valor de fato. Consumido por {@code AuditEventListener}.
 *
 * @param tenantId      tenant da reserva
 * @param reservaId     reserva estornada
 * @param forma         forma da devolução (DINHEIRO, PIX, ...)
 * @param valor         valor devolvido
 * @param observacao    justificativa (obrigatória)
 * @param registradoPor usuário (staff) que registrou
 * @param occurredAt    quando ocorreu
 */
public record EstornoRegistradoEvent(
    UUID tenantId,
    UUID reservaId,
    String forma,
    BigDecimal valor,
    String observacao,
    UUID registradoPor,
    Instant occurredAt
) {
    public static EstornoRegistradoEvent of(
            UUID tenantId, UUID reservaId, String forma, BigDecimal valor,
            String observacao, UUID registradoPor) {
        return new EstornoRegistradoEvent(
            tenantId, reservaId, forma, valor, observacao, registradoPor, Instant.now());
    }
}
