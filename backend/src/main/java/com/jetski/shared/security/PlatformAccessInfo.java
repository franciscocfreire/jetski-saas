package com.jetski.shared.security;

import java.util.List;
import java.util.UUID;

/**
 * Identidade de <strong>plataforma</strong> resolvida para requests {@code /v1/platform/**}.
 *
 * <p>Diferente do {@link TenantAccessInfo}, aqui não existe tenant: o console da plataforma
 * (subdomínio {@code admin.*}) não tem "empresa corrente" — o alvo, quando existe, vem no
 * path ({@code /v1/platform/tenants/{id}/...}). O escopo é global e a decisão é sobre o
 * usuário, não sobre o vínculo dele com uma empresa.
 *
 * <p><strong>Semântica de {@code hasPlatformAccess()}:</strong> hoje (F0) equivale a
 * {@code unrestricted_access = true} — o modelo binário de super admin que já existia.
 * Na F2 (papéis de plataforma granulares) passa a considerar também os papéis
 * {@code PLATFORM_*} em {@link #roles()}, sem que os chamadores precisem mudar.
 *
 * @param usuarioId   ID interno (PostgreSQL) do usuário, ou {@code null} se a identidade
 *                    do JWT não estiver mapeada em {@code usuario_identity_provider}
 * @param roles       papéis globais de {@code usuario_global_roles.roles[]} (nunca null)
 * @param unrestricted {@code true} se {@code usuario_global_roles.unrestricted_access}
 * @since 0.9.0
 */
public record PlatformAccessInfo(UUID usuarioId, List<String> roles, boolean unrestricted) {

    private static final PlatformAccessInfo NONE = new PlatformAccessInfo(null, List.of(), false);

    public PlatformAccessInfo {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    /** Identidade não resolvida (JWT sem mapeamento) — nega acesso de plataforma. */
    public static PlatformAccessInfo none() {
        return NONE;
    }

    /** Usuário sem nenhum papel de plataforma (existe, mas não é operador). */
    public static PlatformAccessInfo semAcesso(UUID usuarioId) {
        return new PlatformAccessInfo(usuarioId, List.of(), false);
    }

    /**
     * Se o usuário pode operar a plataforma. Ver nota de semântica na doc da classe.
     */
    public boolean hasPlatformAccess() {
        return unrestricted;
    }
}
