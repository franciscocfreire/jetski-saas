package com.jetski.shared.exception;

/**
 * CPF informado já pertence a outra conta (customer_profile ou username do
 * Keycloak). HTTP 409 com {@code details.code = "CPF_EM_USO"} para o portal
 * oferecer o fluxo de unificação de contas por OTP em vez de um beco sem
 * saída ("fale com a loja").
 */
public class CpfEmUsoException extends RuntimeException {

    private final boolean mergeDisponivel;

    public CpfEmUsoException(String message, boolean mergeDisponivel) {
        super(message);
        this.mergeDisponivel = mergeDisponivel;
    }

    public boolean isMergeDisponivel() {
        return mergeDisponivel;
    }
}
