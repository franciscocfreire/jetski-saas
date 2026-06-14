package com.jetski.signup.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO para ativação via magic link no fluxo de signup.
 *
 * <p>Contém apenas o magic token (JWT) extraído da URL.
 */
public record MagicActivationRequest(
    @NotBlank(message = "Magic token é obrigatório")
    String magicToken
) {}
