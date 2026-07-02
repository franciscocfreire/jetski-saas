package com.jetski.creditos.api.dto;

import com.jetski.creditos.domain.CreditoCompra;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Solicitação de compra do tenant. */
public record CompraResponse(
        UUID id,
        int quantidade,
        BigDecimal valorPago,
        BigDecimal precoUnitario,
        String pixTxid,
        String status,
        String observacao,
        Instant createdAt,
        Instant decididoEm
) {
    public static CompraResponse from(CreditoCompra c) {
        return new CompraResponse(c.getId(), c.getQuantidade(), c.getValorPago(), c.getPrecoUnitario(),
            c.getPixTxid(), c.getStatus().name(), c.getObservacao(), c.getCreatedAt(), c.getDecididoEm());
    }
}
