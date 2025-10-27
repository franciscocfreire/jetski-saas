package com.jetski.shared.storage;

import com.jetski.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Implementação de armazenamento em filesystem local.
 *
 * Ideal para desenvolvimento local sem dependências externas.
 * Armazena arquivos em: {base-path}/{tenantId}/{locacaoId}/{tipo}.jpg
 *
 * Simula presigned URLs usando tokens temporários armazenados em memória.
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local")
@Slf4j
public class LocalFileStorageService implements StorageService {

    @Value("${storage.local.base-path:/tmp/jetski-photos}")
    private String basePath;

    @Value("${storage.local.presigned-url-expiration-minutes:15}")
    private int presignedUrlExpirationMinutes;

    @Value("${storage.local.max-file-size-mb:10}")
    private long maxFileSizeMb;

    @Value("${server.port:8090}")
    private String serverPort;

    @Override
    public PresignedUrl generatePresignedUploadUrl(String key, String contentType, int expirationMinutes) {
        log.info("Generating local presigned upload URL: key={}", key);

        try {
            // Cria diretório se não existir
            Path filePath = Paths.get(basePath, key);
            Files.createDirectories(filePath.getParent());

            // Gera token temporário (UUID)
            String token = UUID.randomUUID().toString();

            // Simula presigned URL usando endpoint local
            String url = String.format("http://localhost:%s/api/v1/storage/local/upload/%s?token=%s",
                serverPort, key.replace("/", "%2F"), token);

            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expirationMinutes);

            return PresignedUrl.builder()
                .url(url)
                .key(key)
                .expiresAt(expiresAt)
                .method("PUT")
                .contentType(contentType)
                .maxSizeBytes(maxFileSizeMb * 1024 * 1024)
                .build();

        } catch (IOException e) {
            log.error("Failed to create directory for key: {}", key, e);
            throw new BusinessException("Erro ao criar diretório de armazenamento: " + e.getMessage());
        }
    }

    @Override
    public PresignedUrl generatePresignedDownloadUrl(String key, int expirationMinutes) {
        log.info("Generating local presigned download URL: key={}", key);

        if (!fileExists(key)) {
            throw new BusinessException("Arquivo não encontrado: " + key);
        }

        // Gera token temporário (UUID)
        String token = UUID.randomUUID().toString();

        // Simula presigned URL usando endpoint local
        String url = String.format("http://localhost:%s/api/v1/storage/local/download/%s?token=%s",
            serverPort, key.replace("/", "%2F"), token);

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expirationMinutes);

        return PresignedUrl.builder()
            .url(url)
            .key(key)
            .expiresAt(expiresAt)
            .method("GET")
            .build();
    }

    @Override
    public void deleteFile(String key) {
        log.info("Deleting local file: key={}", key);

        try {
            Path filePath = Paths.get(basePath, key);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("File deleted successfully: {}", key);
            } else {
                log.warn("File not found for deletion: {}", key);
            }
        } catch (IOException e) {
            log.error("Failed to delete file: {}", key, e);
            throw new BusinessException("Erro ao deletar arquivo: " + e.getMessage());
        }
    }

    @Override
    public boolean fileExists(String key) {
        Path filePath = Paths.get(basePath, key);
        boolean exists = Files.exists(filePath);
        log.debug("File exists check: key={}, exists={}", key, exists);
        return exists;
    }

    @Override
    public StorageMetadata getFileMetadata(String key) {
        log.info("Getting local file metadata: key={}", key);

        try {
            Path filePath = Paths.get(basePath, key);

            if (!Files.exists(filePath)) {
                throw new BusinessException("Arquivo não encontrado: " + key);
            }

            long sizeBytes = Files.size(filePath);
            FileTime lastModifiedTime = Files.getLastModifiedTime(filePath);
            LocalDateTime lastModified = LocalDateTime.ofInstant(
                lastModifiedTime.toInstant(),
                ZoneId.systemDefault()
            );

            // Tenta inferir content-type pela extensão
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return StorageMetadata.builder()
                .key(key)
                .sizeBytes(sizeBytes)
                .contentType(contentType)
                .lastModified(lastModified)
                .build();

        } catch (IOException e) {
            log.error("Failed to get file metadata: {}", key, e);
            throw new BusinessException("Erro ao obter metadados do arquivo: " + e.getMessage());
        }
    }

    /**
     * Salva arquivo local (usado internamente pelo endpoint de upload simulado).
     */
    public void saveFile(String key, byte[] data) throws IOException {
        Path filePath = Paths.get(basePath, key);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, data);
        log.info("File saved locally: key={}, size={} bytes", key, data.length);
    }

    /**
     * Lê arquivo local (usado internamente pelo endpoint de download simulado).
     */
    public byte[] readFile(String key) throws IOException {
        Path filePath = Paths.get(basePath, key);
        if (!Files.exists(filePath)) {
            throw new BusinessException("Arquivo não encontrado: " + key);
        }
        return Files.readAllBytes(filePath);
    }
}
