package com.jetski.creditos.api.dto;

import com.jetski.creditos.domain.CreditoCompra;

import java.time.Instant;
import java.util.UUID;

/** Solicitação de compra pendente (visão do super admin). */
public record PlatformCompraDTO(
        UUID id,
        UUID tenantId,
        String slug,
        String razaoSocial,
        int quantidade,
        String pixTxid,
        Instant createdAt
) {
    public static PlatformCompraDTO from(CreditoCompra c, String slug, String razaoSocial) {
        return new PlatformCompraDTO(c.getId(), c.getTenantId(), slug, razaoSocial,
            c.getQuantidade(), c.getPixTxid(), c.getCreatedAt());
    }
}
