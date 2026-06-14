package com.jetski.tenant.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO: ComissaoConfigResponse
 *
 * Response with tenant commission and bonus configuration.
 *
 * @author Jetski Team
 * @since 0.12.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComissaoConfigResponse {
    private BigDecimal percentualPadrao;
    private BigDecimal percentualAbaixoBase;
    private Boolean bonusAtivo;
    private Integer bonusMetaVendas;
    private BigDecimal bonusValor;
}
