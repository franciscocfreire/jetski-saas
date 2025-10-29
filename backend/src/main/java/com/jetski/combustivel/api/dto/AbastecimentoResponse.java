package com.jetski.combustivel.api.dto;

import com.jetski.combustivel.domain.TipoAbastecimento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO: AbastecimentoResponse
 *
 * Response containing fuel refill details.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbastecimentoResponse {

    private Long id;
    private UUID tenantId;
    private UUID jetskiId;
    private UUID locacaoId;
    private TipoAbastecimento tipo;

    private BigDecimal litros;
    private BigDecimal precoLitro;
    private BigDecimal custoTotal;

    private Instant dataHora;
    private String observacoes;

    private UUID responsavelId;

    private Instant createdAt;
    private Instant updatedAt;
}
