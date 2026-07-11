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

    /**
     * Salva (upload server-side) um arquivo a partir de bytes em memória.
     *
     * <p>Diferente das presigned URLs (upload feito pelo cliente), este método é
     * usado para artefatos gerados pelo próprio backend — ex.: o PDF consolidado
     * de documentos (atendimento de balcão).
     *
     * @param key chave única do arquivo (ex: tenant_id/reserva_id/documento.pdf)
     * @param content conteúdo do arquivo
     * @param contentType tipo MIME (ex: application/pdf)
     */
    void putObject(String key, byte[] content, String contentType);

    /**
     * Lê os bytes de um objeto armazenado (uso server-side).
     *
     * <p>Ex.: recuperar a imagem da assinatura para embutir no PDF consolidado.
     *
     * @param key chave única do arquivo
     * @return conteúdo do arquivo
     */
    byte[] getObject(String key);

    /**
     * Lista as chaves de objetos sob um prefixo (recursivo). Usado pelo export
     * de tenant (arquivamento pré-reset/expurgo) — prefixo {tenantId}/.
     *
     * @param prefix prefixo das chaves (ex.: "tenant-uuid/")
     * @return chaves completas encontradas
     */
    java.util.List<String> listObjectKeys(String prefix);

    /**
     * Salva um objeto a partir de um stream (uploads grandes — ex.: o zip do
     * export de tenant — sem carregar tudo em memória).
     *
     * @param key         chave única do objeto
     * @param content     stream do conteúdo (o chamador fecha)
     * @param size        tamanho em bytes
     * @param contentType tipo MIME
     */
    void putObject(String key, java.io.InputStream content, long size, String contentType);
}
