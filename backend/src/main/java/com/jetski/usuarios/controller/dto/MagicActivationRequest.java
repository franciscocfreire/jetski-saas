package com.jetski.usuarios.controller.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for magic link activation.
 *
 * <p>This request contains only the magic token (JWT) from the URL.
 * The JWT internally contains the invitation token and temporary password.
 *
 * <p><strong>Usage flow:</strong>
 * <ol>
 *   <li>User receives email with magic link: https://app.com/magic-activate?token=SIGNED_JWT</li>
 *   <li>User clicks link → frontend extracts token from URL</li>
 *   <li>Frontend sends: POST /v1/auth/magic-activate { "magicToken": "..." }</li>
 *   <li>Backend validates JWT → extracts invitation token + temporary password → activates account</li>
 * </ol>
 *
 * <p><strong>Security:</strong><br>
 * The magic token is a signed JWT containing encrypted data.
 * It cannot be forged or tampered with.
 *
 * @author Jetski Team
 * @since 0.5.0
 */
public record MagicActivationRequest(

    @NotBlank(message = "Magic token é obrigatório")
    String magicToken

) {}
