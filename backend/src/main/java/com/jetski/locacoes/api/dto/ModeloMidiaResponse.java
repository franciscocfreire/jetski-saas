package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.ModeloMidia;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for model media
 */
public record ModeloMidiaResponse(
    UUID id,
    UUID modeloId,
    ModeloMidia.TipoMidia tipo,
    String url,
    String thumbnailUrl,
    Integer ordem,
    Boolean principal,
    String titulo,
    Instant createdAt
) {
    /**
     * Factory method to create from entity
     */
    public static ModeloMidiaResponse from(ModeloMidia entity) {
        return new ModeloMidiaResponse(
            entity.getId(),
            entity.getModeloId(),
            entity.getTipo(),
            entity.getUrl(),
            entity.getThumbnailUrl(),
            entity.getOrdem(),
            entity.getPrincipal(),
            entity.getTitulo(),
            entity.getCreatedAt()
        );
    }
}
