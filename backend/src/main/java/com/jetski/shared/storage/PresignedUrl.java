package com.jetski.shared.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Representa uma URL pré-assinada para upload ou download direto de arquivos.
 *
 * Presigned URLs permitem que clientes façam upload/download diretamente
 * para o storage sem passar pelo backend, reduzindo carga e latência.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrl {

    /**
     * URL pré-assinada para upload (PUT) ou download (GET).
     */
    private String url;

    /**
     * Chave única do arquivo no storage.
     */
    private String key;

    /**
     * Data/hora de expiração da URL.
     */
    private LocalDateTime expiresAt;

    /**
     * Método HTTP a ser usado (PUT para upload, GET para download).
     */
    private String method;

    /**
     * Content-Type esperado (apenas para upload).
     */
    private String contentType;

    /**
     * Tamanho máximo permitido em bytes (apenas para upload).
     */
    private Long maxSizeBytes;
}
