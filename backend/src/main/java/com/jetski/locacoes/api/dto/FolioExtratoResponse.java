package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.ReservaLancamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO: extrato do folio (reserva e/ou locação) com resumo.
 * saldo = cobranças − pagamentos + estornos (positivo = cliente deve).
 */
public record FolioExtratoResponse(
    List<FolioLancamentoResponse> lancamentos,
    BigDecimal totalCobrancas,
    BigDecimal totalPagamentos,
    BigDecimal totalEstornos,
    BigDecimal saldo
) {
    public record FolioLancamentoResponse(
        UUID id,
        String tipo,
        String forma,
        BigDecimal valor,
        String observacao,
        UUID registradoPor,
        Instant createdAt
    ) {
        public static FolioLancamentoResponse from(ReservaLancamento l) {
            return new FolioLancamentoResponse(
                l.getId(),
                l.getTipo().name(),
                l.getForma() != null ? l.getForma().name() : null,
                l.getValor(),
                l.getObservacao(),
                l.getRegistradoPor(),
                l.getCreatedAt());
        }
    }

    public static FolioExtratoResponse from(List<ReservaLancamento> lancamentos) {
        BigDecimal cobrancas = BigDecimal.ZERO;
        BigDecimal pagamentos = BigDecimal.ZERO;
        BigDecimal estornos = BigDecimal.ZERO;
        for (ReservaLancamento l : lancamentos) {
            switch (l.getTipo()) {
                case PAGAMENTO -> pagamentos = pagamentos.add(l.getValor());
                case ESTORNO -> estornos = estornos.add(l.getValor());
                default -> cobrancas = cobrancas.add(l.getValor());
            }
        }
        return new FolioExtratoResponse(
            lancamentos.stream().map(FolioLancamentoResponse::from).toList(),
            cobrancas, pagamentos, estornos,
            cobrancas.subtract(pagamentos).add(estornos));
    }
}
