package com.jetski.locacoes.internal.gru;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Resultado da geração da GRU + PIX (saída do {@link GruClient}).
 */
public record GruResultado(
    String gruNumero,        // numeroReferencia do PagTesouro (= nº da GRU)
    BigDecimal gruValor,     // valor a pagar
    String descricao,        // descrição do serviço
    String pixCopiaECola,    // EMV copia-e-cola
    String pixQrPngBase64,   // QR code (PNG base64) — não persistido, só resposta
    Instant pixExpiracao,    // vencimento do PIX
    String idGru             // id interno da Marinha (referência/suporte)
) {}
