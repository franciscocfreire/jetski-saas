package com.jetski.combustivel.api.dto;

import com.jetski.combustivel.domain.TipoAbastecimento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO: AbastecimentoCreateRequest
 *
 * Request to register a new fuel refill.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbastecimentoCreateRequest {

    @NotNull(message = "jetskiId é obrigatório")
    private UUID jetskiId;

    private UUID locacaoId; // Opcional para FROTA

    @NotNull(message = "Tipo de abastecimento é obrigatório")
    private TipoAbastecimento tipo;

    @NotNull(message = "Litros é obrigatório")
    @DecimalMin(value = "0.001", message = "Litros deve ser maior que zero")
    private BigDecimal litros;

    @NotNull(message = "Preço por litro é obrigatório")
    @DecimalMin(value = "0.01", message = "Preço por litro deve ser maior que zero")
    private BigDecimal precoLitro;

    private BigDecimal custoTotal; // Calculado se não fornecido

    @NotNull(message = "Data/hora do abastecimento é obrigatória")
    private Instant dataHora;

    private String observacoes;
}
