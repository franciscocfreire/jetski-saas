package com.jetski.creditos.api.dto;

import java.math.BigDecimal;

/**
 * Solicitação de compra por VALOR: créditos = valor / preço unitário vigente
 * (arredondado para baixo). Txid = comprovante do PIX já transferido.
 */
public record SolicitarCompraRequest(
        BigDecimal valor,
        String pixTxid
) {}
