package com.jetski.usuarios.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Permissões efetivas do usuário autenticado no tenant atual (X-Tenant-Id).
 *
 * As permissões são CRUAS, como definidas no rbac.rego: podem conter os
 * wildcards "*" (acesso total) e "recurso:*" — o cliente aplica o matching
 * (mesma semântica de action_matches_permission).
 *
 * @param tenantId     tenant em que as permissões foram resolvidas
 * @param roles        papéis do usuário nesse tenant
 * @param permissions  permissões cruas (união dos papéis)
 * @param unrestricted true para super admin de plataforma (permissions = ["*"])
 */
public record UserPermissionsResponse(
    UUID tenantId,
    List<String> roles,
    List<String> permissions,
    boolean unrestricted
) {}
