package br.com.jetski.keycloak.emailcode;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * O Keycloak 26 recusa duas credenciais do mesmo tipo com o mesmo userLabel. O rótulo
 * vem do User-Agent, então duas máquinas com o mesmo navegador/SO colidem de verdade —
 * a segunda precisa de sufixo, nunca de recusa nem de derrubar a primeira.
 */
class TrustedDeviceEnrollAuthenticatorTest {

    @Test
    void rotuloLivreFicaComoEsta() {
        assertEquals("Chrome · Windows",
            TrustedDeviceEnrollAuthenticator.rotuloUnico(Set.of("Chrome · Linux"), "Chrome · Windows"));
    }

    @Test
    void rotuloEmUsoGanhaSufixo() {
        assertEquals("Chrome · Windows (2)",
            TrustedDeviceEnrollAuthenticator.rotuloUnico(Set.of("Chrome · Windows"), "Chrome · Windows"));
    }

    @Test
    void sufixoPulaOsJaOcupados() {
        assertEquals("Chrome · Windows (4)",
            TrustedDeviceEnrollAuthenticator.rotuloUnico(
                Set.of("Chrome · Windows", "Chrome · Windows (2)", "Chrome · Windows (3)"),
                "Chrome · Windows"));
    }
}
