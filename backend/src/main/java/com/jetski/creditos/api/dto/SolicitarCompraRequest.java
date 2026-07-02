package com.jetski.creditos.api.dto;

/** Solicitação de compra: quantidade desejada + txid do PIX já transferido. */
public record SolicitarCompraRequest(
        int quantidade,
        String pixTxid
) {}
