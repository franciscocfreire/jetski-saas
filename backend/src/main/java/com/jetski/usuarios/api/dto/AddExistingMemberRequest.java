package com.jetski.usuarios.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * DTO: Add Existing Member Request
 *
 * Adiciona um usuário JÁ EXISTENTE (que já tem conta) como membro do tenant,
 * sem novo cadastro/convite. Útil para um usuário gerenciar mais de uma empresa.
 *
 * @author Jetski Team
 */
@Schema(description = "Requisição para adicionar um usuário já existente como membro do tenant")
public record AddExistingMemberRequest(
    @Schema(description = "Email do usuário já cadastrado", example = "fulano@empresa.com",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    String email,

    @Schema(description = "Papéis a atribuir no tenant", example = "[\"ADMIN_TENANT\"]",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "Pelo menos um papel deve ser informado")
    List<String> papeis
) {}
