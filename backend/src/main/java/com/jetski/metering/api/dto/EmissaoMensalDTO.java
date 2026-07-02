package com.jetski.metering.api.dto;

/**
 * Uso mensal do tenant. {@code total} = documento + gru (prévia não é cobrável;
 * exibida à parte como sinal de acompanhamento).
 */
public record EmissaoMensalDTO(
        String competencia,   // "YYYY-MM"
        long documento,
        long gru,
        long previa,
        long total
) {}
