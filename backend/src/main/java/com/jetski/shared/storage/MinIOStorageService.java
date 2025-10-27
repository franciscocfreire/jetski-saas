package com.jetski.shared.storage;

import com.jetski.shared.exception.BusinessException;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * Implementação de armazenamento usando MinIO (S3-compatible).
 *
 * MinIO é 100% compatível com AWS S3 API, permitindo desenvolvimento
 * local ou staging com mesma interface que produção.
 *
 * Roda em Docker Compose para ambiente dev.
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
@Slf4j
public class MinIOStorageService implements StorageService {

    private final MinioClient minioClient;

    @Value("${storage.minio.bucket}")
    private String bucket;

    @Value("${storage.minio.presigned-url-expiration-minutes:15}")
    private int presignedUrlExpirationMinutes;

    public MinIOStorageService(
        @Value("${storage.minio.endpoint}") String endpoint,
        @Value("${storage.minio.access-key}") String accessKey,
        @Value("${storage.minio.secret-key}") String secretKey
    ) {
        log.info("Initializing MinIO client: endpoint={}", endpoint);
        this.minioClient = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
    }

    @Override
    public PresignedUrl generatePresignedUploadUrl(String key, String contentType, int expirationMinutes) {
        log.info("Generating MinIO presigned upload URL: bucket={}, key={}", bucket, key);

        try {
            // Garante que o bucket existe
            ensureBucketExists();

            // Gera presigned URL para PUT
            String url = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(key)
                    .expiry(expirationMinutes, TimeUnit.MINUTES)
                    .build()
            );

            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expirationMinutes);

            log.info("MinIO presigned upload URL generated: key={}, expiresAt={}", key, expiresAt);

            return PresignedUrl.builder()
                .url(url)
                .key(key)
                .expiresAt(expiresAt)
                .method("PUT")
                .contentType(contentType)
                .build();

        } catch (Exception e) {
            log.error("Failed to generate MinIO presigned upload URL: key={}", key, e);
            throw new BusinessException("Erro ao gerar URL de upload: " + e.getMessage());
        }
    }

    @Override
    public PresignedUrl generatePresignedDownloadUrl(String key, int expirationMinutes) {
        log.info("Generating MinIO presigned download URL: bucket={}, key={}", bucket, key);

        try {
            // Gera presigned URL para GET
            String url = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry(expirationMinutes, TimeUnit.MINUTES)
                    .build()
            );

            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expirationMinutes);

            log.info("MinIO presigned download URL generated: key={}, expiresAt={}", key, expiresAt);

            return PresignedUrl.builder()
                .url(url)
                .key(key)
                .expiresAt(expiresAt)
                .method("GET")
                .build();

        } catch (Exception e) {
            log.error("Failed to generate MinIO presigned download URL: key={}", key, e);
            throw new BusinessException("Erro ao gerar URL de download: " + e.getMessage());
        }
    }

    @Override
    public void deleteFile(String key) {
        log.info("Deleting MinIO object: bucket={}, key={}", bucket, key);

        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build()
            );

            log.info("MinIO object deleted successfully: key={}", key);

        } catch (Exception e) {
            log.error("Failed to delete MinIO object: key={}", key, e);
            throw new BusinessException("Erro ao deletar arquivo: " + e.getMessage());
        }
    }

    @Override
    public boolean fileExists(String key) {
        log.debug("Checking if MinIO object exists: bucket={}, key={}", bucket, key);

        try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build()
            );
            log.debug("MinIO object exists: key={}", key);
            return true;

        } catch (Exception e) {
            log.debug("MinIO object not found: key={}", key);
            return false;
        }
    }

    @Override
    public StorageMetadata getFileMetadata(String key) {
        log.info("Getting MinIO object metadata: bucket={}, key={}", bucket, key);

        try {
            StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build()
            );

            LocalDateTime lastModified = LocalDateTime.ofInstant(
                stat.lastModified().toInstant(),
                ZoneId.systemDefault()
            );

            return StorageMetadata.builder()
                .key(key)
                .sizeBytes(stat.size())
                .contentType(stat.contentType())
                .lastModified(lastModified)
                .etag(stat.etag())
                .build();

        } catch (Exception e) {
            log.error("Failed to get MinIO object metadata: key={}", key, e);
            throw new BusinessException("Erro ao obter metadados do arquivo: " + e.getMessage());
        }
    }

    /**
     * Garante que o bucket existe, criando se necessário.
     */
    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                    .bucket(bucket)
                    .build()
            );

            if (!exists) {
                log.info("Bucket does not exist, creating: bucket={}", bucket);
                minioClient.makeBucket(
                    MakeBucketArgs.builder()
                        .bucket(bucket)
                        .build()
                );
                log.info("Bucket created successfully: bucket={}", bucket);
            }

        } catch (Exception e) {
            log.error("Failed to ensure bucket exists: bucket={}", bucket, e);
            throw new BusinessException("Erro ao verificar/criar bucket: " + e.getMessage());
        }
    }
}
