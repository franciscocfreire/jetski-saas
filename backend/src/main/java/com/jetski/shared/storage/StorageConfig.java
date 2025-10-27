package com.jetski.shared.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuração de storage.
 *
 * A implementação de StorageService é selecionada automaticamente via
 * @ConditionalOnProperty em cada classe de implementação:
 *
 * - storage.type=local  → LocalFileStorageService
 * - storage.type=minio  → MinIOStorageService
 * - storage.type=s3     → S3StorageService (futuro)
 *
 * Esta classe apenas loga a configuração ativa.
 */
@Configuration
@Slf4j
public class StorageConfig {

    @Value("${storage.type:local}")
    private String storageType;

    @PostConstruct
    public void init() {
        log.info("===========================================");
        log.info("Storage Configuration Initialized");
        log.info("Storage Type: {}", storageType);
        log.info("===========================================");

        switch (storageType) {
            case "local":
                log.info("Using LocalFileStorageService (filesystem-based)");
                log.info("Perfect for local development - zero external dependencies");
                break;
            case "minio":
                log.info("Using MinIOStorageService (S3-compatible)");
                log.info("Requires MinIO running in Docker Compose");
                break;
            case "s3":
                log.info("Using S3StorageService (AWS S3)");
                log.info("For production use with AWS");
                break;
            default:
                log.warn("Unknown storage type: {}, defaulting to local", storageType);
        }
    }
}
