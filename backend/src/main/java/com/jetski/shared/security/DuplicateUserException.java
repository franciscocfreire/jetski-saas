package com.jetski.shared.security;

/**
 * Lançada pelo provisioning quando o identity provider já tem uma conta com o
 * mesmo e-mail/username (HTTP 409 do Keycloak). Permite ao chamador dar uma
 * mensagem de negócio adequada sem acoplar ao provedor.
 */
public class DuplicateUserException extends RuntimeException {

    public DuplicateUserException(String email) {
        super("Usuário já existe no provedor de identidade: " + email);
    }
}
