package com.jetski.shared.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Metadados de um arquivo armazenado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageMetadata {

    /**
     * Chave única do arquivo no storage.
     */
    private String key;

    /**
     * Tamanho do arquivo em bytes.
     */
    private Long sizeBytes;

    /**
     * Content-Type (MIME type) do arquivo.
     */
    private String contentType;

    /**
     * Hash SHA-256 do arquivo (para validação de integridade).
     */
    private String sha256Hash;

    /**
     * Data/hora da última modificação.
     */
    private LocalDateTime lastModified;

    /**
     * ETag do arquivo (usado por S3/MinIO para versionamento).
     */
    private String etag;
}
