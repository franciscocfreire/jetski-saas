package com.jetski.reservas.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: recebimento registrado no folio da LOCAÇÃO — acerto do
 * check-out (saldo de combustível/hora extra) ou pagamento integral de
 * walk-in. Consumido por {@code AuditEventListener}.
 *
 * @param tenantId      tenant
 * @param locacaoId     locação paga
 * @param reservaId     reserva de origem (null em walk-in)
 * @param forma         forma de pagamento (DINHEIRO, PIX, ...)
 * @param valor         valor recebido
 * @param observacao    observação do staff (opcional)
 * @param registradoPor usuário (staff) que registrou
 * @param occurredAt    quando ocorreu
 */
public record PagamentoLocacaoRegistradoEvent(
    UUID tenantId,
    UUID locacaoId,
    UUID reservaId,
    String forma,
    BigDecimal valor,
    String observacao,
    UUID registradoPor,
    Instant occurredAt
) {
    public static PagamentoLocacaoRegistradoEvent of(
            UUID tenantId, UUID locacaoId, UUID reservaId, String forma,
            BigDecimal valor, String observacao, UUID registradoPor) {
        return new PagamentoLocacaoRegistradoEvent(
            tenantId, locacaoId, reservaId, forma, valor, observacao, registradoPor, Instant.now());
    }
}
