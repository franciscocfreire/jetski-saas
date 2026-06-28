package com.jetski.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecretCipher - AES-GCM em repouso")
class SecretCipherTest {

    @Test
    @DisplayName("roundtrip: cifra e decifra com a mesma chave")
    void roundtrip() {
        SecretCipher c = new SecretCipher("chave-secreta-de-teste", "");
        String enc = c.encrypt("minha-senha-de-app");
        assertThat(enc).startsWith("enc:v1:").isNotEqualTo("minha-senha-de-app");
        assertThat(c.decrypt(enc)).isEqualTo("minha-senha-de-app");
    }

    @Test
    @DisplayName("IV aleatório: dois ciphertexts diferentes para o mesmo texto")
    void ivAleatorio() {
        SecretCipher c = new SecretCipher("k", "");
        assertThat(c.encrypt("x")).isNotEqualTo(c.encrypt("x"));
    }

    @Test
    @DisplayName("legado/texto-puro (sem prefixo) é retornado como está")
    void legadoTextoPuro() {
        SecretCipher c = new SecretCipher("k", "");
        assertThat(c.decrypt("senha-antiga-em-texto-puro")).isEqualTo("senha-antiga-em-texto-puro");
    }

    @Test
    @DisplayName("sem chave configurada → no-op (texto puro)")
    void semChave() {
        SecretCipher c = new SecretCipher("", "");
        assertThat(c.isEnabled()).isFalse();
        assertThat(c.encrypt("s")).isEqualTo("s");
        assertThat(c.decrypt("s")).isEqualTo("s");
    }

    @Test
    @DisplayName("rotação: chave nova decifra valor cifrado com a anterior (keyring)")
    void rotacaoKeyring() {
        // Cifrado com a chave antiga.
        String comAntiga = new SecretCipher("chave-antiga", "").encrypt("senha");
        // Após rotação: SECRET_KEY=nova, PREVIOUS=antiga.
        SecretCipher rotacionado = new SecretCipher("chave-nova", "chave-antiga");
        assertThat(rotacionado.decrypt(comAntiga)).isEqualTo("senha");
        // Novos saves usam a nova; ainda decifram.
        assertThat(rotacionado.decrypt(rotacionado.encrypt("senha2"))).isEqualTo("senha2");
        // Sem a anterior, o valor antigo não decifra mais.
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new SecretCipher("chave-nova", "").decrypt(comAntiga))
            .isInstanceOf(IllegalStateException.class);
    }
}
