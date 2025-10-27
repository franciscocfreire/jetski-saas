package com.jetski.shared.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response com presigned URL para upload de foto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response com presigned URL para upload de foto")
public class UploadUrlResponse {

    @Schema(description = "ID da foto gerado pelo backend", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID fotoId;

    @Schema(description = "URL pré-assinada para upload (PUT)", example = "https://s3.amazonaws.com/bucket/key?X-Amz-Signature=...")
    private String uploadUrl;

    @Schema(description = "Chave do arquivo no storage", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/123e4567-e89b-12d3-a456-426614174000/CHECKIN_FRENTE.jpg")
    private String key;

    @Schema(description = "Data/hora de expiração da URL", example = "2025-10-26T14:30:00")
    private LocalDateTime expiresAt;

    @Schema(description = "Tamanho máximo permitido em bytes", example = "10485760")
    private Long maxSizeBytes;

    @Schema(description = "Content-Type esperado", example = "image/jpeg")
    private String contentType;
}
