package com.jetski.locacoes.domain;

/**
 * Exibição de telefone brasileiro nos textos que saem da plataforma (ofício à
 * Capitania, e-mail ao cliente). O cadastro aceita o que o usuário digitou —
 * máscara, só dígitos ou E.164 — e a saída é sempre {@code (11) 95586-8220} ou
 * {@code (11) 3333-4444}.
 */
public final class Telefones {

    private Telefones() {}

    /**
     * Formata para exibição. Número estrangeiro ({@code +} com outro código de país)
     * ou com quantidade de dígitos que não é de telefone brasileiro volta como veio.
     */
    public static String formatar(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return telefone;
        }
        String original = telefone.trim();
        String d = original.replaceAll("\\D", "");
        if (original.startsWith("+") && !d.startsWith("55")) {
            return original;
        }
        if (d.startsWith("55") && (d.length() == 12 || d.length() == 13)) {
            d = d.substring(2);
        }
        if (d.length() == 11) {
            return "(" + d.substring(0, 2) + ") " + d.substring(2, 7) + "-" + d.substring(7);
        }
        if (d.length() == 10) {
            return "(" + d.substring(0, 2) + ") " + d.substring(2, 6) + "-" + d.substring(6);
        }
        return original;
    }
}
