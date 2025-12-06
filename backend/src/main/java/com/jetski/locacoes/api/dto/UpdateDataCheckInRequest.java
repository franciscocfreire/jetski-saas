package com.jetski.locacoes.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO: UpdateDataCheckInRequest
 *
 * Request to update the check-in date/time of an existing rental.
 * Only allowed for rentals with status EM_CURSO.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDataCheckInRequest {

    /**
     * New check-in date/time
     */
    @NotNull(message = "Data de check-in é obrigatória")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dataCheckIn;
}
