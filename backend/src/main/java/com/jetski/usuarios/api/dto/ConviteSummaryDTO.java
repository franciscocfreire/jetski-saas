package com.jetski.usuarios.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO: Invitation Summary
 *
 * Summary information about a pending invitation.
 *
 * @author Jetski Team
 * @since 0.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resumo de um convite pendente")
public class ConviteSummaryDTO {

    @Schema(description = "ID do convite", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Email do convidado", example = "novo.usuario@empresa.com")
    private String email;

    @Schema(description = "Nome do convidado", example = "João Silva")
    private String nome;

    @Schema(description = "Papéis atribuídos", example = "[\"OPERADOR\", \"VENDEDOR\"]")
    private List<String> papeis;

    @Schema(description = "Data de criação do convite")
    private Instant createdAt;

    @Schema(description = "Data de expiração do convite")
    private Instant expiresAt;

    @Schema(description = "Status do convite", example = "PENDING", allowableValues = {"PENDING", "EXPIRED"})
    private String status;

    @Schema(description = "Quantidade de emails enviados", example = "1")
    private Integer emailSentCount;

    @Schema(description = "Data do último envio de email")
    private Instant lastEmailSentAt;
}
