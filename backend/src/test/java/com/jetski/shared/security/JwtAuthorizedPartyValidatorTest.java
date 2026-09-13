package com.jetski.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0 da revisão técnica (ago/2026): a API aceitava qualquer token assinado pelo
 * realm. O validador de {@code azp}/{@code aud} restringe aos clients legítimos.
 */
@DisplayName("JwtAuthorizedPartyValidator: só tokens emitidos para clients legítimos")
class JwtAuthorizedPartyValidatorTest {

    private static final List<String> PROD = List.of(
        "jetski-backoffice", "jetski-customer-portal", "jetski-platform-console",
        "jetski-mobile", "jetski-api");

    private final JwtAuthorizedPartyValidator validator = new JwtAuthorizedPartyValidator(PROD);

    @Test
    @DisplayName("azp de client legítimo: aceito")
    void azpLegitimo() {
        for (String client : PROD) {
            assertThat(validator.validate(jwt(b -> b.claim("azp", client))).hasErrors())
                .as("azp=%s", client).isFalse();
        }
    }

    @Test
    @DisplayName("azp de client não listado (jetski-test, grafana, password-check): rejeitado")
    void azpNaoListado() {
        for (String client : List.of("jetski-test", "grafana", "jetski-password-check", "admin-cli")) {
            var result = validator.validate(jwt(b -> b.claim("azp", client)));
            assertThat(result.hasErrors()).as("azp=%s", client).isTrue();
            assertThat(result.getErrors().iterator().next().getErrorCode()).isEqualTo("invalid_token");
        }
    }

    @Test
    @DisplayName("azp manda: aud com client legítimo não salva token emitido para outro client")
    void azpTemPrecedenciaSobreAud() {
        var result = validator.validate(jwt(b -> b.claim("azp", "jetski-test")
            .audience(List.of("jetski-api"))));
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("sem azp: aceita só se algum aud for de client legítimo")
    void semAzpUsaAud() {
        assertThat(validator.validate(jwt(b -> b.audience(List.of("account", "jetski-api")))).hasErrors())
            .isFalse();
        assertThat(validator.validate(jwt(b -> b.audience(List.of("account")))).hasErrors())
            .isTrue();
        assertThat(validator.validate(jwt(b -> { })).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("allowlist vazia ou nula: nega tudo (fail-closed)")
    void allowlistVaziaNegaTudo() {
        assertThat(new JwtAuthorizedPartyValidator(List.of())
            .validate(jwt(b -> b.claim("azp", "jetski-backoffice"))).hasErrors()).isTrue();
        assertThat(new JwtAuthorizedPartyValidator(null)
            .validate(jwt(b -> b.claim("azp", "jetski-backoffice"))).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("entradas em branco/espaços na configuração são normalizadas")
    void normalizaConfiguracao() {
        var v = new JwtAuthorizedPartyValidator(List.of(" jetski-test ", "", "  "));
        assertThat(v.clientsPermitidos()).containsExactly("jetski-test");
    }

    private static Jwt jwt(Consumer<Jwt.Builder> customizer) {
        Jwt.Builder b = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300));
        customizer.accept(b);
        return b.build();
    }
}
