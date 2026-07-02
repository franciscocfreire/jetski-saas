package com.jetski.creditos.api.dto;

import com.jetski.creditos.domain.CreditoLancamento;

import java.time.Instant;
import java.util.UUID;

/** Linha do extrato de créditos. */
public record LancamentoResponse(
        UUID id,
        String tipo,
        int quantidade,
        int saldoApos,
        String motivo,
        Instant createdAt
) {
    public static LancamentoResponse from(CreditoLancamento l) {
        return new LancamentoResponse(l.getId(), l.getTipo().name(), l.getQuantidade(),
            l.getSaldoApos(), l.getMotivo(), l.getCreatedAt());
    }
}
