package com.jetski.locacoes.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO: Response for bulk commission payment
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoLoteResponse {

    private UUID vendedorId;
    private String nomeVendedor;
    private Integer qtdComissoesPagas;
    private BigDecimal valorTotalPago;
    private Instant dataHoraPagamento;
    private String referenciaPagamento;
}
