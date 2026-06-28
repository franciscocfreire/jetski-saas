package com.jetski.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecretCipher - AES-GCM em repouso")
class SecretCipherTest {

    @Test
    @DisplayName("roundtrip: cifra e decifra com a mesma chave")
    void roundtrip() {
        SecretCipher c = new SecretCipher("chave-secreta-de-teste");
        String enc = c.encrypt("minha-senha-de-app");
        assertThat(enc).startsWith("enc:v1:").isNotEqualTo("minha-senha-de-app");
        assertThat(c.decrypt(enc)).isEqualTo("minha-senha-de-app");
    }

    @Test
    @DisplayName("IV aleatório: dois ciphertexts diferentes para o mesmo texto")
    void ivAleatorio() {
        SecretCipher c = new SecretCipher("k");
        assertThat(c.encrypt("x")).isNotEqualTo(c.encrypt("x"));
    }

    @Test
    @DisplayName("legado/texto-puro (sem prefixo) é retornado como está")
    void legadoTextoPuro() {
        SecretCipher c = new SecretCipher("k");
        assertThat(c.decrypt("senha-antiga-em-texto-puro")).isEqualTo("senha-antiga-em-texto-puro");
    }

    @Test
    @DisplayName("sem chave configurada → no-op (texto puro)")
    void semChave() {
        SecretCipher c = new SecretCipher("");
        assertThat(c.isEnabled()).isFalse();
        assertThat(c.encrypt("s")).isEqualTo("s");
        assertThat(c.decrypt("s")).isEqualTo("s");
    }
}
