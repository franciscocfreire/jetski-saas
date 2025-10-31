package com.jetski.fechamento.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for consolidating a specific day
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidarDiaRequest {

    @NotNull(message = "Data de referência é obrigatória")
    private LocalDate dtReferencia;
}
