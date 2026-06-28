package com.jetski.locacoes.internal.gru;

/**
 * Resultado da geração do boleto da GRU (PDF).
 * {@code gruNumero} é o número de referência extraído do PDF (best-effort; null se
 * não encontrado — o boleto não expõe o número por HTTP, só impresso no PDF).
 */
public record GruBoletoResultado(
    String idGru,
    String gruNumero,
    byte[] pdf
) {}
