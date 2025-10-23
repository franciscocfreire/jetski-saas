package com.jetski.usuarios.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO: Complete Activation Request (Option 2 Flow)
 *
 * Request to complete account activation with temporary password.
 *
 * Option 2 Flow:
 * 1. User receives email with token + temporary password
 * 2. User provides both token and temporary password
 * 3. Backend validates temporary password matches stored hash
 * 4. If valid: creates Keycloak user with temp password + UPDATE_PASSWORD required action
 * 5. User must change password on first login (Keycloak enforces)
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteActivationRequest {

    @NotBlank(message = "Token é obrigatório")
    private String token;

    @NotBlank(message = "Senha temporária é obrigatória")
    private String temporaryPassword;
}
