package com.jetski.usuarios.api.dto;

import lombok.Builder;

import java.util.UUID;

/**
 * DTO: perfil self-service do usuário staff (GET/PUT /v1/user/me).
 *
 * <p>{@code senhaGerenciavel} = tem credencial de senha própria no provedor
 * (conta criada só via Google não tem — a seção de troca de senha fica oculta).
 * {@code idpFederado} = tem vínculo Google (IdP broker) — distingue a mensagem
 * "conta Google" de "troca de senha temporariamente indisponível".
 *
 * <p>{@code avatarDataUrl} = data URL base64 para exibição direta em img
 * (mesmo padrão do logo de branding do tenant); null se não houver avatar.
 */
@Builder
public record UserProfileResponse(
    UUID id,
    String nome,
    String email,
    Boolean emailVerified,
    String telefone,
    String avatarDataUrl,
    boolean senhaGerenciavel,
    boolean idpFederado
) {}
