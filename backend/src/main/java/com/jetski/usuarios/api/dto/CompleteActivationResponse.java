package com.jetski.usuarios.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO: Complete Activation Response
 *
 * Response after successfully completing account activation with password.
 * Contains user info and OAuth2 tokens for automatic login.
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteActivationResponse {

    private UUID usuarioId;
    private String email;
    private String nome;
    private UUID tenantId;
    private String[] roles;
    private String message;

    // OAuth2 tokens for automatic login (optional - returned if configured)
    private String accessToken;
    private String refreshToken;
    private String idToken;
    private Long expiresIn;

    public static CompleteActivationResponse success(
        UUID usuarioId,
        String email,
        String nome,
        UUID tenantId,
        String[] roles
    ) {
        return CompleteActivationResponse.builder()
            .usuarioId(usuarioId)
            .email(email)
            .nome(nome)
            .tenantId(tenantId)
            .roles(roles)
            .message("Conta ativada com sucesso! Você já pode fazer login.")
            .build();
    }

    public static CompleteActivationResponse successWithTokens(
        UUID usuarioId,
        String email,
        String nome,
        UUID tenantId,
        String[] roles,
        String accessToken,
        String refreshToken,
        String idToken,
        Long expiresIn
    ) {
        return CompleteActivationResponse.builder()
            .usuarioId(usuarioId)
            .email(email)
            .nome(nome)
            .tenantId(tenantId)
            .roles(roles)
            .message("Conta ativada com sucesso! Login automático realizado.")
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .idToken(idToken)
            .expiresIn(expiresIn)
            .build();
    }
}
