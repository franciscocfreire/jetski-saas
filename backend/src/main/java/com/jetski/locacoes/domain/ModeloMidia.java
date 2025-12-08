package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: ModeloMidia
 *
 * Represents a media item (image or video) associated with a jetski model.
 * Supports multiple images and videos per model for marketplace display.
 *
 * Types supported:
 * - IMAGEM: Static images (S3, CDN)
 * - VIDEO: Video content (YouTube, Vimeo, S3)
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Entity
@Table(name = "modelo_midia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModeloMidia {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "modelo_id", nullable = false)
    private UUID modeloId;

    /**
     * Type of media: IMAGEM or VIDEO
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoMidia tipo;

    /**
     * URL of the media content
     * For images: S3 presigned URL or CDN URL
     * For videos: YouTube/Vimeo embed URL or S3 URL
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    /**
     * Thumbnail URL for videos
     * Optional - can be auto-generated from YouTube/Vimeo
     */
    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    /**
     * Display order (lower = first)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer ordem = 0;

    /**
     * Is this the main/featured image for the model?
     * Only one media item per model should be principal
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean principal = false;

    /**
     * Optional title/alt text for accessibility
     */
    @Column(length = 255)
    private String titulo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Enum for media types
     */
    public enum TipoMidia {
        IMAGEM,
        VIDEO
    }
}
