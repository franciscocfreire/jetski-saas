package com.jetski.locacoes.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: prévia de documento gerada ({@code EmissaoService.preview}).
 *
 * <p>Prévias não são cobráveis, mas são contadas pelo módulo {@code metering} como
 * sinal antifraude: razão alta de prévias × emissões indica operação via prévia
 * para evitar a emissão contabilizada. A prévia sai sempre com marca d'água.
 *
 * @param tenantId   tenant da reserva
 * @param reservaId  reserva pré-visualizada (referência do metering)
 * @param destino    "MARINHA" ou "CLIENTE"
 * @param occurredAt instante da geração
 */
public record DocumentoPreviewGeradoEvent(
    UUID tenantId,
    UUID reservaId,
    String destino,
    Instant occurredAt
) {}
