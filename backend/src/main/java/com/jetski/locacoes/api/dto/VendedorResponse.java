package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.TipoChavePix;
import com.jetski.locacoes.domain.VendedorTipo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO: Vendedor Response
 *
 * Response containing seller/partner details.
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendedorResponse {

    private UUID id;
    private UUID tenantId;
    private String nome;
    private String documento;
    private String email;
    private String telefone;

    /**
     * PIX key for payment transfers.
     */
    private String chavePix;

    /**
     * Type of PIX key (CPF, CNPJ, EMAIL, TELEFONE, ALEATORIA).
     */
    private TipoChavePix tipoChavePix;

    private VendedorTipo tipo;

    /**
     * Default commission percentage (extracted from regraComissaoJson).
     * Simplified field for frontend display.
     */
    private BigDecimal comissaoPercentual;

    /**
     * Full commission rules JSON for advanced configuration.
     */
    private String regraComissaoJson;

    /**
     * Valor base da diária do vendedor.
     * Usado no controle de presença diária.
     */
    private BigDecimal diariaBase;

    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
}
