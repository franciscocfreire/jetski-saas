package com.jetski.shared.security;

/**
 * O e-mail já pertence a uma identidade que NÃO é de cliente (população staff).
 *
 * <p>Regra do projeto: as populações staff ({@code Usuario}+{@code Membro}) e
 * clientes (role {@code CLIENTE}) nunca se cruzam — uma conta staff jamais pode
 * ser vinculada a um cliente, mesmo com o mesmo e-mail.
 */
public class IdentityConflictException extends RuntimeException {

    public IdentityConflictException(String message) {
        super(message);
    }
}
