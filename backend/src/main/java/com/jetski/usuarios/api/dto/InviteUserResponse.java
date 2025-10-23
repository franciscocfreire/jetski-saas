package com.jetski.usuarios.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO: Invite User Response
 *
 * Response after successfully inviting a user.
 * Contains invitation details and expiration info.
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteUserResponse {

    private UUID conviteId;
    private String email;
    private String nome;
    private String[] papeis;
    private Instant expiresAt;
    private String message;

    public static InviteUserResponse success(UUID conviteId, String email, String nome, String[] papeis, Instant expiresAt) {
        return InviteUserResponse.builder()
            .conviteId(conviteId)
            .email(email)
            .nome(nome)
            .papeis(papeis)
            .expiresAt(expiresAt)
            .message("Convite enviado com sucesso. O usuário tem 48 horas para ativar a conta.")
            .build();
    }
}
