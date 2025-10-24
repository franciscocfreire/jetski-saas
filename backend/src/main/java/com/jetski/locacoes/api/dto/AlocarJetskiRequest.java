package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO: Alocar Jetski Request
 *
 * Request to allocate a specific jetski to a reservation.
 *
 * Used when:
 * - Customer arrives for check-in (FIFO allocation)
 * - Operator needs to pre-assign a specific jetski
 * - Customer requested a particular unit
 *
 * Business Rules:
 * - Jetski must belong to the same modelo as the reservation
 * - Jetski must be DISPONIVEL (available)
 * - Reservation must be CONFIRMADA
 * - Cannot allocate if already has jetski_id
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlocarJetskiRequest {

    @NotNull(message = "Jetski ID é obrigatório")
    private UUID jetskiId;
}
