package com.jetski.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cifra/decifra segredos em repouso (ex.: senha SMTP do tenant) com AES-256-GCM.
 *
 * <p>A chave AES é derivada (SHA-256) do segredo {@code jetski.secret-key}
 * (env {@code JETSKI_SECRET_KEY}), que vive FORA do banco. Sem chave configurada
 * (dev) o cipher é no-op (guarda em texto puro) — então em produção basta definir
 * {@code JETSKI_SECRET_KEY} para ligar a criptografia.
 *
 * <p>Formato: {@code enc:v1:base64(iv|ciphertext+tag)}. Valores sem o prefixo são
 * tratados como legado/texto puro e retornados como estão (migração transparente).
 */
@Component
public class SecretCipher {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LEN = 12;     // GCM IV recomendado: 96 bits
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${jetski.secret-key:}") String secret) {
        this.key = (secret == null || secret.isBlank()) ? null : deriveKey(secret);
    }

    /** True se há chave configurada (criptografia ativa). */
    public boolean isEnabled() {
        return key != null;
    }

    /** Cifra. Sem chave → devolve o texto puro (no-op de dev). */
    public String encrypt(String plain) {
        if (plain == null || key == null) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao cifrar segredo", e);
        }
    }

    /** Decifra. Valor sem prefixo (legado/texto puro) é retornado como está. */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX) || key == null) {
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(all, 0, IV_LEN);
            byte[] ct = Arrays.copyOfRange(all, IV_LEN, all.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao decifrar segredo", e);
        }
    }

    private static SecretKey deriveKey(String secret) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao derivar a chave de criptografia", e);
        }
    }
}
