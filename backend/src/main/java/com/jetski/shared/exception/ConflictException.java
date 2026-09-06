package com.jetski.shared.exception;

/**
 * Exception for resource conflict scenarios (HTTP 409).
 *
 * Use when an operation cannot be completed due to a conflict
 * with the current state of a resource.
 *
 * Examples:
 * - Duplicate email invitation
 * - Resource already exists
 * - Concurrent modification conflict
 *
 * @author Jetski Team
 * @since 0.4.0
 */
public class ConflictException extends RuntimeException {

    /**
     * Código estável do conflito (ex.: {@code EMAIL_JA_CADASTRADO}), exposto em
     * {@code details.code}. Existe para o cliente escolher o que oferecer a seguir
     * sem casar a mensagem por string — o texto muda, o código não. Nulo quando o
     * conflito não tem tratamento específico do outro lado.
     */
    private final String code;

    public ConflictException(String message) {
        this(message, (String) null);
    }

    public ConflictException(String message, String code) {
        super(message);
        this.code = code;
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
    }

    public String getCode() {
        return code;
    }
}
