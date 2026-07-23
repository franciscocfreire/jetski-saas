package com.jetski.usuarios.api;

import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.security.TenantContext;
import com.jetski.usuarios.api.dto.UserPermissionsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller: permissões efetivas do usuário no tenant atual.
 *
 * Fonte de verdade: mapa role_permissions do OPA (rbac.rego) — o mesmo que o
 * ABACAuthorizationInterceptor usa para o enforcement. Consumido pelo menu do
 * backoffice para exibir apenas as operações permitidas (filtro de UX; o
 * enforcement real continua no interceptor, requisição a requisição).
 *
 * Rota fora de /v1/user/me/** de propósito: o TenantFilter pula esse prefixo e
 * aqui precisamos do TenantContext populado (roles resolvidos via X-Tenant-Id).
 * A ação user:permissions é pulada no ABACAuthorizationInterceptor (payload
 * derivado exclusivamente dos papéis do próprio usuário).
 *
 * @author Jetski Team
 */
@RestController
@RequestMapping("/v1/user/permissions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User", description = "User account and tenant management")
public class UserPermissionsController {

    private final OPAAuthorizationService opaAuthorizationService;

    /**
     * GET /api/v1/user/permissions
     *
     * Permissões efetivas (cruas, com wildcards) do usuário autenticado no
     * tenant do X-Tenant-Id. Super admin (unrestricted) recebe ["*"].
     */
    @GetMapping
    @Operation(
        summary = "Effective permissions in the current tenant",
        description = "Returns the raw permission list (may contain \"*\" and " +
            "\"resource:*\" wildcards) for the authenticated user's roles in the " +
            "tenant identified by X-Tenant-Id.",
        security = @SecurityRequirement(name = "bearer-jwt")
    )
    public ResponseEntity<UserPermissionsResponse> getMyPermissions() {
        boolean unrestricted = TenantContext.isUnrestricted();
        List<String> roles = TenantContext.getUserRoles();

        List<String> permissions = unrestricted
            ? List.of("*")
            : opaAuthorizationService.getUserPermissions(roles);

        return ResponseEntity.ok(new UserPermissionsResponse(
            TenantContext.getTenantId(),
            roles,
            permissions,
            unrestricted
        ));
    }
}
