package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO: Update Reserva Request
 *
 * Request to update an existing reservation.
 *
 * All fields are optional. Only provided fields will be updated.
 * Can only update PENDENTE or CONFIRMADA reservations.
 *
 * Business Rules:
 * - If dates changed, validates no conflicts
 * - Start date must be before end date
 * - Cannot update CANCELADA or FINALIZADA reservations
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaUpdateRequest {

    /**
     * Novo modelo (opcional). Só pode ser alterado enquanto a reserva é RASCUNHO.
     */
    private UUID modeloId;

    /**
     * New start date/time (optional)
     * If changed, conflict detection will be performed
     */
    private LocalDateTime dataInicio;

    /**
     * New end date/time (optional)
     * If changed, conflict detection will be performed
     */
    private LocalDateTime dataFimPrevista;

    @Size(max = 1000, message = "Observações devem ter no máximo 1000 caracteres")
    private String observacoes;
}
