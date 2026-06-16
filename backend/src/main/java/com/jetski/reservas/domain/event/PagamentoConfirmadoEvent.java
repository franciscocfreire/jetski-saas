package com.jetski.reservas.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: Pagamento da reserva confirmado (sinal ou total).
 *
 * <p>Publicado quando o staff confirma o pagamento de uma reserva
 * (remoto via comprovante PIX, ou presencial no balcão).
 * Consumido por {@code AuditEventListener}.
 *
 * @param tenantId      tenant da reserva
 * @param reservaId     reserva confirmada
 * @param tipo          tipo do pagamento (SINAL | TOTAL)
 * @param valorPago     valor confirmado
 * @param confirmadoPor usuário (staff) que confirmou
 * @param occurredAt    quando ocorreu
 */
public record PagamentoConfirmadoEvent(
    UUID tenantId,
    UUID reservaId,
    String tipo,
    BigDecimal valorPago,
    UUID confirmadoPor,
    Instant occurredAt
) {
    public static PagamentoConfirmadoEvent of(
            UUID tenantId, UUID reservaId, String tipo, BigDecimal valorPago, UUID confirmadoPor) {
        return new PagamentoConfirmadoEvent(tenantId, reservaId, tipo, valorPago, confirmadoPor, Instant.now());
    }
}
