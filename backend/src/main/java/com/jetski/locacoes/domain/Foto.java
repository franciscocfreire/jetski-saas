package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: Foto (Photo/Image)
 *
 * Represents photos taken during rental operations (check-in, check-out, incidents).
 * Photos are stored in AWS S3 and accessed via presigned URLs.
 *
 * Use Cases:
 * - Check-in photos: Document jetski condition BEFORE rental (mandatory)
 * - Check-out photos: Document jetski condition AFTER rental (mandatory)
 * - Incident photos: Document damage or issues during rental (optional)
 * - Maintenance photos: Document repairs or inspections (optional)
 *
 * Storage Strategy:
 * - S3 bucket: jetski-fotos-{env} (e.g., jetski-fotos-prod)
 * - Key format: {tenant_id}/locacao/{locacao_id}/{tipo}_{timestamp}_{uuid}.jpg
 * - Example: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/locacao/123/CHECK_IN_20250125_abc123.jpg
 * - Presigned URLs: Generated on-demand with 5-minute expiration
 *
 * Integrity:
 * - SHA-256 hash stored for verification
 * - File metadata (size, content type) for validation
 *
 * Business Rules:
 * - CHECK_IN and CHECK_OUT photos are mandatory (4 photos each: front, back, left, right)
 * - Photos are immutable once uploaded (no edit, only delete)
 * - Tenant isolation: URLs only accessible by authorized tenant users
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Entity
@Table(name = "foto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Foto {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Rental operation this photo belongs to
     */
    @Column(name = "locacao_id")
    private UUID locacaoId;

    /**
     * Jetski in the photo (for maintenance photos without locacao)
     */
    @Column(name = "jetski_id")
    private UUID jetskiId;

    /**
     * Type of photo (check-in, check-out, incident, maintenance)
     */
    @Column(name = "tipo", nullable = false, length = 20)
    @Convert(converter = FotoTipoConverter.class)
    private FotoTipo tipo;

    /**
     * Public URL for accessing the photo (with presigned URL or CloudFront)
     */
    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    /**
     * S3 object key (full path in bucket)
     * Format: {tenant_id}/locacao/{locacao_id}/{tipo}_{timestamp}_{uuid}.ext
     */
    @Column(name = "s3_key", nullable = false, columnDefinition = "TEXT")
    private String s3Key;

    /**
     * Original filename uploaded by user
     */
    @Column(name = "filename", nullable = false)
    private String filename;

    /**
     * MIME content type (e.g., image/jpeg, image/png)
     */
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    /**
     * File size in bytes
     */
    @Column(name = "size_bytes")
    private Long sizeBytes;

    /**
     * SHA-256 hash of file content for integrity verification
     */
    @Column(name = "sha256_hash", length = 64)
    private String sha256Hash;

    /**
     * Timestamp when file was successfully uploaded to S3
     */
    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    /**
     * Record creation timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // ===================================================================
    // Business Logic Helpers
    // ===================================================================

    /**
     * Check if photo has been successfully uploaded
     */
    public boolean isUploaded() {
        return this.uploadedAt != null;
    }

    /**
     * Check if photo is check-in type
     */
    public boolean isCheckIn() {
        return this.tipo == FotoTipo.CHECK_IN;
    }

    /**
     * Check if photo is check-out type
     */
    public boolean isCheckOut() {
        return this.tipo == FotoTipo.CHECK_OUT;
    }

    /**
     * Check if photo is incident type
     */
    public boolean isIncidente() {
        return this.tipo == FotoTipo.INCIDENTE;
    }

    /**
     * Mark photo as uploaded with metadata
     */
    public void markAsUploaded(Long sizeBytes, String sha256Hash) {
        this.uploadedAt = Instant.now();
        this.sizeBytes = sizeBytes;
        this.sha256Hash = sha256Hash;
    }
}
