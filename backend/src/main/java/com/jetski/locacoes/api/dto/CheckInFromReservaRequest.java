package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO: CheckInFromReservaRequest
 *
 * Request to perform check-in from an existing reservation.
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInFromReservaRequest {

    /**
     * Reservation ID to convert to rental
     */
    @NotNull(message = "Reserva ID é obrigatório")
    private UUID reservaId;

    /**
     * Hourmeter reading at check-in (e.g., 100.5 hours)
     */
    @NotNull(message = "Horímetro inicial é obrigatório")
    @DecimalMin(value = "0.0", message = "Horímetro deve ser positivo")
    private BigDecimal horimetroInicio;

    /**
     * Optional notes for check-in
     */
    private String observacoes;
}
