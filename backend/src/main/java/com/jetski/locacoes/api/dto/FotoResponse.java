package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.FotoTipo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response com dados de uma foto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dados de uma foto")
public class FotoResponse {

    @Schema(description = "ID da foto", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "ID da locação", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID locacaoId;

    @Schema(description = "Tipo da foto")
    private FotoTipo tipo;

    @Schema(description = "Chave do arquivo no storage", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/123e4567-e89b-12d3-a456-426614174000/CHECKIN_FRENTE.jpg")
    private String storageKey;

    @Schema(description = "URL pré-assinada para download (GET)", example = "https://s3.amazonaws.com/bucket/key?X-Amz-Signature=...")
    private String downloadUrl;

    @Schema(description = "Tamanho do arquivo em bytes", example = "2048576")
    private Long tamanhoBytes;

    @Schema(description = "Content-Type do arquivo", example = "image/jpeg")
    private String contentType;

    @Schema(description = "Hash SHA-256 do arquivo", example = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    private String sha256Hash;

    @Schema(description = "Data/hora de upload", example = "2025-10-26T13:45:00")
    private LocalDateTime uploadedAt;

    @Schema(description = "Data/hora de expiração da URL de download", example = "2025-10-26T14:45:00")
    private LocalDateTime downloadUrlExpiresAt;
}
