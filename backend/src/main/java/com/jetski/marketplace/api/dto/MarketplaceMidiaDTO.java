package com.jetski.marketplace.api.dto;

import java.util.UUID;

/**
 * DTO for media items in marketplace (images/videos)
 */
public record MarketplaceMidiaDTO(
    UUID id,
    String tipo,        // IMAGEM or VIDEO
    String url,
    String thumbnailUrl,
    Integer ordem,
    Boolean principal,
    String titulo
) {
    /**
     * Factory method to create from raw data
     */
    public static MarketplaceMidiaDTO of(
        UUID id,
        String tipo,
        String url,
        String thumbnailUrl,
        Integer ordem,
        Boolean principal,
        String titulo
    ) {
        return new MarketplaceMidiaDTO(id, tipo, url, thumbnailUrl, ordem, principal, titulo);
    }
}
