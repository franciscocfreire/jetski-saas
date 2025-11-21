package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO: CheckInWalkInRequest
 *
 * Request to perform walk-in check-in (without prior reservation).
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInWalkInRequest {

    /**
     * Jetski ID to rent
     */
    @NotNull(message = "Jetski ID é obrigatório")
    private UUID jetskiId;

    /**
     * Cliente ID (customer)
     */
    @NotNull(message = "Cliente ID é obrigatório")
    private UUID clienteId;

    /**
     * Optional Vendedor ID (seller/partner)
     */
    private UUID vendedorId;

    /**
     * Hourmeter reading at check-in
     */
    @NotNull(message = "Horímetro inicial é obrigatório")
    @DecimalMin(value = "0.0", message = "Horímetro deve ser positivo")
    private BigDecimal horimetroInicio;

    /**
     * Expected rental duration in minutes
     */
    @NotNull(message = "Duração prevista é obrigatória")
    @Min(value = 15, message = "Duração mínima é 15 minutos")
    private Integer duracaoPrevista;

    /**
     * Optional notes
     */
    private String observacoes;

    /**
     * Check-in checklist (JSON array of verification items)
     * Example: ["motor_ok", "casco_ok", "gasolina_ok", "equipamentos_ok"]
     * RN05: Mandatory for check-out validation
     */
    private String checklistSaidaJson;
}
