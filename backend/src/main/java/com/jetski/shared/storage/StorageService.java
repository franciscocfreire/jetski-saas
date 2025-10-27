package com.jetski.shared.storage;

/**
 * Interface de abstração para serviços de armazenamento de arquivos.
 *
 * Suporta múltiplas implementações:
 * - LocalFileStorageService: armazenamento em filesystem local (dev)
 * - MinIOStorageService: armazenamento S3-compatible via MinIO (staging)
 * - S3StorageService: armazenamento AWS S3 (produção) [futuro]
 *
 * A implementação é selecionada via Spring Profile (storage.type: local|minio|s3).
 */
public interface StorageService {

    /**
     * Gera uma presigned URL para upload direto ao storage.
     *
     * @param key chave única do arquivo (ex: tenant_id/locacao_id/tipo.jpg)
     * @param contentType tipo MIME do arquivo (ex: image/jpeg)
     * @param expirationMinutes tempo de expiração da URL em minutos
     * @return PresignedUrl contendo URL assinada e metadados
     */
    PresignedUrl generatePresignedUploadUrl(String key, String contentType, int expirationMinutes);

    /**
     * Gera uma presigned URL para download direto do storage.
     *
     * @param key chave única do arquivo
     * @param expirationMinutes tempo de expiração da URL em minutos
     * @return PresignedUrl contendo URL assinada e metadados
     */
    PresignedUrl generatePresignedDownloadUrl(String key, int expirationMinutes);

    /**
     * Remove um arquivo do storage.
     *
     * @param key chave única do arquivo
     */
    void deleteFile(String key);

    /**
     * Verifica se um arquivo existe no storage.
     *
     * @param key chave única do arquivo
     * @return true se o arquivo existe, false caso contrário
     */
    boolean fileExists(String key);

    /**
     * Retorna metadados de um arquivo armazenado.
     *
     * @param key chave única do arquivo
     * @return StorageMetadata contendo tamanho, hash, contentType, etc.
     */
    StorageMetadata getFileMetadata(String key);
}
