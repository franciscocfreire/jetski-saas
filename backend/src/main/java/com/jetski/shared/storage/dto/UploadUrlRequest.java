package com.jetski.shared.storage.dto;

import com.jetski.locacoes.domain.FotoTipo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request para gerar presigned URL de upload de foto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request para gerar presigned URL de upload de foto")
public class UploadUrlRequest {

    @NotNull(message = "locacaoId é obrigatório")
    @Schema(description = "ID da locação", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID locacaoId;

    @NotNull(message = "tipoFoto é obrigatório")
    @Schema(description = "Tipo da foto (CHECKIN_FRENTE, CHECKIN_LATERAL_ESQ, etc.)")
    private FotoTipo tipoFoto;

    @NotNull(message = "contentType é obrigatório")
    @Pattern(regexp = "^image/(jpeg|png|webp)$", message = "Content-Type deve ser image/jpeg, image/png ou image/webp")
    @Schema(description = "Content-Type do arquivo", example = "image/jpeg", allowableValues = {"image/jpeg", "image/png", "image/webp"})
    private String contentType;

    @NotNull(message = "fileSize é obrigatório")
    @Positive(message = "fileSize deve ser maior que zero")
    @Schema(description = "Tamanho do arquivo em bytes", example = "2048576")
    private Long fileSize;

    @NotNull(message = "sha256Hash é obrigatório")
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "sha256Hash deve ser um hash SHA-256 válido (64 caracteres hexadecimais)")
    @Schema(description = "Hash SHA-256 do arquivo para validação de integridade", example = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    private String sha256Hash;
}
