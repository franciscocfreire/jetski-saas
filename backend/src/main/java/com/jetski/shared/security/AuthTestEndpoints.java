package com.jetski.shared.security;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Scaffolding de teste de autenticação/autorização ({@link AuthTestController}).
 *
 * <p>O controller só existe nos perfis {@code local}, {@code test} e {@code dev}
 * (mesmo padrão do {@code TestEmailController}). As exceções de segurança do
 * endpoint público dele — chain público do {@link SecurityConfig}, exclusão do
 * ABAC no {@code WebMvcConfig} e o pulo de tenant no {@code TenantFilter} — também
 * só valem nesses perfis: em produção, {@code /v1/auth-test/**} não tem handler
 * nem whitelist nenhuma (cai no chain protegido como qualquer rota desconhecida).
 */
public final class AuthTestEndpoints {

    /** Perfis em que o scaffolding existe. Manter igual ao {@code @Profile} do controller. */
    public static final String[] PERFIS = {"local", "test", "dev"};

    /** Único endpoint sem autenticação do controller. */
    public static final String PUBLIC_PATH = "/v1/auth-test/public";

    private AuthTestEndpoints() {
    }

    /** {@code true} se algum perfil de dev/teste está ativo (nunca em {@code prod}). */
    public static boolean habilitado(Environment environment) {
        return environment != null && environment.acceptsProfiles(Profiles.of(PERFIS));
    }
}
