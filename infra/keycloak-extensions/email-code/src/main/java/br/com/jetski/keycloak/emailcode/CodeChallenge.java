package br.com.jetski.keycloak.emailcode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Lógica pura do desafio por código de e-mail — sem dependência de Keycloak,
 * para ser unit-testável (roda no `mvn package` do docker build).
 *
 * Parâmetros espelham as convenções de OTP do backend (CustomerCpfMergeService /
 * AceiteOtpService): 6 dígitos, TTL 10 min, máx 5 tentativas, cooldown 60 s.
 */
public final class CodeChallenge {

    public static final int CODE_LENGTH = 6;
    public static final int TTL_MINUTES = 10;
    public static final long TTL_SECONDS = TTL_MINUTES * 60L;
    public static final int MAX_ATTEMPTS = 5;
    public static final long RESEND_COOLDOWN_SECONDS = 60L;

    private static final SecureRandom RNG = new SecureRandom();

    private CodeChallenge() {
    }

    /** Como o cliente se identificou na tela 1. */
    public enum Kind {
        EMAIL,
        CPF,
        UNKNOWN
    }

    /** Identificador classificado + valor normalizado (CPF só dígitos, e-mail trim/lowercase). */
    public record Identifier(Kind kind, String value) {
    }

    /**
     * Classifica o que o cliente digitou: 11 dígitos (com ou sem pontuação) = CPF;
     * contém "@" = e-mail; resto = UNKNOWN (o authenticator tenta username e e-mail).
     */
    public static Identifier classify(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Identifier(Kind.UNKNOWN, "");
        }
        String trimmed = raw.trim();
        String digits = trimmed.replaceAll("[.\\-\\s/]", "");
        if (digits.matches("\\d{11}")) {
            return new Identifier(Kind.CPF, digits);
        }
        if (trimmed.contains("@")) {
            return new Identifier(Kind.EMAIL, trimmed.toLowerCase());
        }
        return new Identifier(Kind.UNKNOWN, trimmed);
    }

    /** Gera código de 6 dígitos com zero à esquerda (mesma convenção do backend). */
    public static String generateCode() {
        return String.format("%06d", RNG.nextInt(1_000_000));
    }

    /** SHA-256 hex do código — só o hash vai para as auth notes (sessão é persistida em banco). */
    public static String hash(String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /**
     * Verificação em tempo constante. Null-safe: com hash ausente (conta
     * inexistente) compara contra um dummy para não abrir oráculo de timing.
     */
    public static boolean verify(String expectedHash, String typedCode) {
        String typed = typedCode == null ? "" : typedCode.trim();
        String typedHash = hash(typed);
        if (expectedHash == null || expectedHash.isEmpty()) {
            // consome o mesmo tempo de comparação e sempre falha
            MessageDigest.isEqual(typedHash.getBytes(StandardCharsets.UTF_8),
                    hash("000000dummy").getBytes(StandardCharsets.UTF_8));
            return false;
        }
        return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8),
                typedHash.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isExpired(long expiresAtEpochSec, long nowEpochSec) {
        return nowEpochSec > expiresAtEpochSec;
    }
}
