package com.jetski.locacoes.internal.gru;

/**
 * Resultado da geração do boleto da GRU (PDF).
 */
public record GruBoletoResultado(
    String idGru,
    byte[] pdf
) {}
