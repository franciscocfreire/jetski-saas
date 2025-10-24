package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO: Confirmar Sinal Request
 *
 * Request to confirm deposit payment for a reservation.
 *
 * When a deposit is confirmed:
 * - Reservation priority upgrades from BAIXA to ALTA
 * - Reservation becomes guaranteed (blocks physical capacity)
 * - No longer subject to automatic expiration
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmarSinalRequest {

    @NotNull(message = "Valor do sinal é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor do sinal deve ser maior que zero")
    private BigDecimal valorSinal;
}
