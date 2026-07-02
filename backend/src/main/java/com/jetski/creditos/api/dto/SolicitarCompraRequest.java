package com.jetski.creditos.api.dto;

/**
 * Solicitação de compra POR QUANTIDADE: o tenant escolhe quantas emissões quer;
 * o valor a transferir (quantidade × preço vigente) é apresentado pela UI.
 * Txid = comprovante do PIX transferido.
 */
public record SolicitarCompraRequest(
        int quantidade,
        String pixTxid
) {}
