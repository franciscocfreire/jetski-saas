package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.JetskiStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating jetski status
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JetskiStatusUpdateRequest {

    @NotNull(message = "Status é obrigatório")
    private JetskiStatus status;
}
