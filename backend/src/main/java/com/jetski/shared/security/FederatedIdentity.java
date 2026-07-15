package com.jetski.shared.security;

/**
 * Identidade federada (IdP broker) de um usuário no provedor de identidade —
 * representação provider-agnóstica para os módulos de negócio não dependerem
 * de classes do Keycloak.
 *
 * @param idpAlias    alias do identity provider no realm (ex.: "google")
 * @param idpUserId   id do usuário NO IdP externo (ex.: sub do Google)
 * @param idpUserName username/e-mail no IdP externo
 */
public record FederatedIdentity(String idpAlias, String idpUserId, String idpUserName) {
}
