package com.jetski.usuarios.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * DTO: Update Member Roles Request
 *
 * Request body for updating a member's roles.
 * At least one role must be provided.
 *
 * @author Jetski Team
 * @since 0.4.0
 */
@Schema(description = "Requisição para atualizar papéis de um membro")
public record UpdateRolesRequest(
    @Schema(
        description = "Lista de papéis a atribuir ao membro",
        example = "[\"GERENTE\", \"OPERADOR\"]",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotEmpty(message = "Pelo menos um papel deve ser informado")
    List<String> papeis
) {}
