package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO: AssociarClienteRequest
 *
 * Request para associar um cliente a uma locação existente.
 * Usado quando o check-in foi feito sem cliente (check-in rápido).
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssociarClienteRequest {

    /**
     * ID do cliente a ser associado
     */
    @NotNull(message = "Cliente ID é obrigatório")
    private UUID clienteId;
}
