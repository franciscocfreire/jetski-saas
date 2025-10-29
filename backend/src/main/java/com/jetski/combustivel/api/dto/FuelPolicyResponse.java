package com.jetski.combustivel.api.dto;

import com.jetski.combustivel.domain.FuelChargeMode;
import com.jetski.combustivel.domain.FuelPolicyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO: FuelPolicyResponse
 *
 * Response containing fuel charging policy details.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FuelPolicyResponse {

    private Long id;
    private UUID tenantId;
    private String nome;

    private FuelChargeMode tipo;
    private FuelPolicyType aplicavelA;
    private UUID referenciaId;

    private BigDecimal valorTaxaPorHora;
    private Boolean comissionavel;
    private Boolean ativo;
    private Integer prioridade;
    private String descricao;

    private Instant createdAt;
    private Instant updatedAt;
}
