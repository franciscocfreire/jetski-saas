package com.jetski.creditos.api.dto;

/**
 * Solicitação de compra POR QUANTIDADE: o tenant escolhe quantas emissões quer;
 * o valor a transferir (quantidade × preço vigente) é apresentado pela UI.
 * O comprovante do PIX (foto/PDF, data-URL ou base64 puro) é obrigatório;
 * o txid é opcional (legado).
 */
public record SolicitarCompraRequest(
        int quantidade,
        String pixTxid,
        String comprovanteBase64
) {}
