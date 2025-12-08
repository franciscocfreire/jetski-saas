package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.ModeloMidia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating/updating model media
 */
public record ModeloMidiaRequest(
    @NotNull(message = "Tipo é obrigatório")
    ModeloMidia.TipoMidia tipo,

    @NotBlank(message = "URL é obrigatória")
    String url,

    String thumbnailUrl,

    Integer ordem,

    Boolean principal,

    String titulo
) {
    /**
     * Create with defaults for optional fields
     */
    public ModeloMidiaRequest {
        if (ordem == null) ordem = 0;
        if (principal == null) principal = false;
    }
}
