package com.jetski.controller;

import com.jetski.controller.dto.UserTenantsResponse;
import com.jetski.domain.entity.Membro;
import com.jetski.service.TenantAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

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
@Tag(name = "User", description = "User account and tenant management")
public class UserTenantsController {

    private final TenantAccessService tenantAccessService;

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

        UUID usuarioId = UUID.fromString(jwt.getSubject());

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

        return ResponseEntity.ok(
            UserTenantsResponse.limited(membros, count)
        );
    }
}
