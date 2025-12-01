package com.jetski.usuarios.api;

import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.Tenant;
import com.jetski.usuarios.api.dto.TenantSummary;
import com.jetski.usuarios.api.dto.UserTenantsResponse;
import com.jetski.usuarios.domain.Membro;
import com.jetski.usuarios.internal.IdentityProviderMappingService;
import com.jetski.usuarios.internal.TenantAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller: User Tenants
 *
 * Provides endpoints for users to list their accessible tenants.
 *
 * Use cases:
 * - Mobile app: Show tenant selector on login
 * - Web app: Show tenant dropdown
 * - Super admin: Indicate unrestricted access (use search instead)
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@RestController
@RequestMapping("/v1/user/tenants")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User", description = "User account and tenant management")
public class UserTenantsController {

    private final TenantAccessService tenantAccessService;
    private final IdentityProviderMappingService identityMappingService;
    private final TenantQueryService tenantQueryService;

    /**
     * GET /api/v1/user/tenants
     *
     * Lists all tenants the authenticated user can access.
     *
     * Response types:
     * - LIMITED: Returns list of tenants (max 100)
     * - UNRESTRICTED: User is platform admin, can access any tenant
     *
     * For users with 10k+ tenants, use tenant search API instead.
     *
     * @param jwt Authenticated user JWT
     * @return UserTenantsResponse with access type and tenant list
     */
    @GetMapping
    @Operation(
        summary = "List user's accessible tenants",
        description = "Returns all tenants the user can access. " +
            "For platform admins, returns UNRESTRICTED indicator.",
        security = @SecurityRequirement(name = "bearer-jwt")
    )
    public ResponseEntity<UserTenantsResponse> listUserTenants(
            @AuthenticationPrincipal Jwt jwt) {

        // DEBUG: Log JWT details (using WARN to ensure it shows)
        log.warn("🔍 JWT DEBUG - TOKEN COMPLETO: {}", jwt != null ? jwt.getTokenValue() : "JWT is null");
        log.warn("🔍 JWT DEBUG - claims: {}", jwt != null ? jwt.getClaims() : "JWT is null");
        log.warn("🔍 JWT DEBUG - subject: {}", jwt != null ? jwt.getSubject() : "JWT is null");
        log.warn("🔍 JWT DEBUG - issuer: {}", jwt != null ? jwt.getIssuer() : "JWT is null");

        // Resolve Keycloak UUID to PostgreSQL UUID using identity provider mapping
        // Try getSubject() first, fallback to "sub" claim directly
        String providerUserId = jwt.getSubject();
        if (providerUserId == null && jwt.hasClaim("sub")) {
            providerUserId = jwt.getClaimAsString("sub");
            log.warn("🔍 JWT DEBUG - Got sub from claims directly: {}", providerUserId);
        }

        // WORKAROUND: se sub não existe, use email para buscar usuário
        // O Keycloak não está incluindo 'sub' no access token mesmo com mapper
        if (providerUserId == null) {
            if (jwt.hasClaim("email")) {
                String email = jwt.getClaimAsString("email");
                log.warn("⚠️ JWT sem 'sub' claim! Usando email como fallback: {}", email);
                // Buscar usuário por email ao invés de por provider_user_id
                UUID usuarioId = identityMappingService.resolveUsuarioIdByEmail(email);
                log.warn("🔍 JWT DEBUG - Resolved usuarioId by email: {}", usuarioId);

                // Count total tenants
                long count = tenantAccessService.countUserTenants(usuarioId);

                if (count == -1) {
                    // Super admin with unrestricted access
                    return ResponseEntity.ok(
                        UserTenantsResponse.unrestricted()
                    );
                }

                // Normal user - list specific tenants
                List<Membro> membros = tenantAccessService.listUserTenants(usuarioId);

                // Fetch tenant details
                List<UUID> tenantIds = membros.stream()
                    .map(Membro::getTenantId)
                    .collect(Collectors.toList());

                Map<UUID, Tenant> tenantsMap = tenantQueryService.findTenantsById(tenantIds);

                // Build TenantSummary list
                List<TenantSummary> tenantSummaries = buildTenantSummaries(membros, tenantsMap);

                return ResponseEntity.ok(
                    UserTenantsResponse.limited(tenantSummaries, count)
                );
            } else {
                throw new RuntimeException("JWT sem 'sub' nem 'email' claim. Impossível identificar usuário.");
            }
        }

        log.warn("🔍 JWT DEBUG - Final providerUserId: {}", providerUserId);
        UUID usuarioId = identityMappingService.resolveUsuarioId("keycloak", providerUserId);

        // Count total tenants
        long count = tenantAccessService.countUserTenants(usuarioId);

        if (count == -1) {
            // Super admin with unrestricted access
            return ResponseEntity.ok(
                UserTenantsResponse.unrestricted()
            );
        }

        // Normal user - list specific tenants
        List<Membro> membros = tenantAccessService.listUserTenants(usuarioId);

        // Fetch tenant details
        List<UUID> tenantIds = membros.stream()
            .map(Membro::getTenantId)
            .collect(Collectors.toList());

        Map<UUID, Tenant> tenantsMap = tenantQueryService.findTenantsById(tenantIds);

        // Build TenantSummary list
        List<TenantSummary> tenantSummaries = buildTenantSummaries(membros, tenantsMap);

        return ResponseEntity.ok(
            UserTenantsResponse.limited(tenantSummaries, count)
        );
    }

    /**
     * Build TenantSummary list from Membro and Tenant data.
     */
    private List<TenantSummary> buildTenantSummaries(List<Membro> membros, Map<UUID, Tenant> tenantsMap) {
        return membros.stream()
            .map(membro -> {
                Tenant tenant = tenantsMap.get(membro.getTenantId());
                return TenantSummary.builder()
                    .id(membro.getTenantId())
                    .slug(tenant != null ? tenant.getSlug() : null)
                    .razaoSocial(tenant != null ? tenant.getRazaoSocial() : null)
                    .status(tenant != null && tenant.getStatus() != null ? tenant.getStatus().name() : null)
                    .roles(membro.getPapeis() != null ? List.of(membro.getPapeis()) : List.of())
                    .build();
            })
            .collect(Collectors.toList());
    }
}
