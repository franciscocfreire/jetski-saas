package com.jetski.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0 da revisão técnica (ago/2026): o AuthTestController (sondagem da matriz OPA)
 * e as whitelists de segurança do endpoint público dele não podem existir em prod.
 */
@DisplayName("AuthTestEndpoints: scaffolding de teste só em local/test/dev")
class AuthTestEndpointsTest {

    @Test
    @DisplayName("controller anotado com @Profile igual aos perfis das whitelists (sem prod)")
    void controllerSoEmPerfisDeDev() {
        Profile profile = AuthTestController.class.getAnnotation(Profile.class);
        assertThat(profile).as("AuthTestController precisa de @Profile").isNotNull();
        assertThat(profile.value()).containsExactlyInAnyOrder(AuthTestEndpoints.PERFIS);
        assertThat(profile.value()).doesNotContain("prod");
    }

    @Test
    @DisplayName("whitelist desligada em prod e sem perfil; ligada em dev/test/local")
    void habilitadoPorPerfil() {
        assertThat(AuthTestEndpoints.habilitado(env("prod"))).isFalse();
        assertThat(AuthTestEndpoints.habilitado(new MockEnvironment())).isFalse();
        assertThat(AuthTestEndpoints.habilitado(null)).isFalse();

        assertThat(AuthTestEndpoints.habilitado(env("dev"))).isTrue();
        assertThat(AuthTestEndpoints.habilitado(env("test"))).isTrue();
        assertThat(AuthTestEndpoints.habilitado(env("local"))).isTrue();
    }

    private static MockEnvironment env(String perfil) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(perfil);
        return env;
    }
}
