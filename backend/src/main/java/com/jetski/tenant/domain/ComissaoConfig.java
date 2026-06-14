package com.jetski.tenant.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Configuration for commission and bonus settings per tenant.
 * Stored as JSONB in tenant.comissao_config column.
 *
 * @param percentualPadrao Commission percentage for sales at or above base price (default 10%)
 * @param percentualAbaixoBase Commission percentage for sales below base price (default 5%)
 * @param bonusAtivo Whether the bonus system is enabled
 * @param bonusMetaVendas Number of sales above base price required to earn a bonus
 * @param bonusValor Bonus value in tenant currency (e.g., R$ 500.00)
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ComissaoConfig(
        @JsonProperty("percentualPadrao") BigDecimal percentualPadrao,
        @JsonProperty("percentualAbaixoBase") BigDecimal percentualAbaixoBase,
        @JsonProperty("bonusAtivo") Boolean bonusAtivo,
        @JsonProperty("bonusMetaVendas") Integer bonusMetaVendas,
        @JsonProperty("bonusValor") BigDecimal bonusValor
) {

    /**
     * Default configuration values
     */
    public static ComissaoConfig padrao() {
        return new ComissaoConfig(
                new BigDecimal("10.0"),
                new BigDecimal("5.0"),
                true,
                50,
                new BigDecimal("500.00")
        );
    }

    /**
     * Validates the configuration
     */
    @JsonIgnore
    public boolean isValid() {
        return percentualPadrao != null && percentualPadrao.compareTo(BigDecimal.ZERO) >= 0
                && percentualAbaixoBase != null && percentualAbaixoBase.compareTo(BigDecimal.ZERO) >= 0
                && bonusAtivo != null
                && (bonusMetaVendas == null || bonusMetaVendas > 0)
                && (bonusValor == null || bonusValor.compareTo(BigDecimal.ZERO) >= 0);
    }
}
