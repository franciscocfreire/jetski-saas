package com.jetski.locacoes.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: GRU gerada na Marinha (geração real, com custo — PIX ou boleto).
 *
 * <p>Publicado APENAS no sucesso "fresco" de {@code GruService.gerarGru}/{@code gerarBoleto}
 * — reaproveitamento de GRU válida e falhas NÃO publicam. PIX e boleto gerados para a
 * mesma habilitação são duas gerações reais na Marinha e contam duas vezes.
 *
 * <p>Consumido pelo módulo {@code metering} (contagem de uso por tenant, tipo GRU).
 *
 * @param tenantId       tenant dono da reserva
 * @param reservaId      reserva da habilitação
 * @param habilitacaoId  id de reserva_habilitacao (referência do metering)
 * @param meio           "PIX" ou "BOLETO"
 * @param geradaEm       instante da geração (gru_gerada_em — chave de idempotência)
 */
public record GruEmitidaEvent(
    UUID tenantId,
    UUID reservaId,
    UUID habilitacaoId,
    String meio,
    Instant geradaEm
) {}
