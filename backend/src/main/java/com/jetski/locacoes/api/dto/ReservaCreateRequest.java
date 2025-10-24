package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO: Create Reserva Request
 *
 * Request to create a new jetski reservation/booking.
 *
 * Business Rules (v0.3.0):
 * - Reservation is BY MODELO (not specific jetski)
 * - Specific jetski allocation is optional (can be done at check-in)
 * - Optional deposit system (ALTA vs BAIXA priority)
 * - Overbooking allowed for reservations without deposit
 * - Start date must be before end date
 * - Start date cannot be in the past
 *
 * @author Jetski Team
 * @since 0.2.0
 * @version 0.3.0 - Refactored to modelo-based booking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaCreateRequest {

    @NotNull(message = "Modelo é obrigatório")
    private UUID modeloId;

    /**
     * Optional specific jetski (if customer wants a particular unit)
     * If not provided, jetski will be allocated at check-in
     */
    private UUID jetskiId;

    @NotNull(message = "Cliente é obrigatório")
    private UUID clienteId;

    /**
     * Seller/partner who created the reservation (optional)
     * Used for commission calculation when reservation converts to rental
     */
    private UUID vendedorId;

    @NotNull(message = "Data de início é obrigatória")
    @Future(message = "Data de início deve ser futura")
    private LocalDateTime dataInicio;

    @NotNull(message = "Data de fim prevista é obrigatória")
    @Future(message = "Data de fim prevista deve ser futura")
    private LocalDateTime dataFimPrevista;

    @Size(max = 1000, message = "Observações devem ter no máximo 1000 caracteres")
    private String observacoes;
}
