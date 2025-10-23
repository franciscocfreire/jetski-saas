package com.jetski.usuarios.api;

import com.jetski.shared.security.TenantContext;
import com.jetski.usuarios.api.dto.InviteUserRequest;
import com.jetski.usuarios.api.dto.InviteUserResponse;
import com.jetski.usuarios.internal.UserInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller: User Invitation
 *
 * REST API for inviting users to tenants.
 * Requires authentication and ADMIN_TENANT or GERENTE role.
 *
 * Endpoints:
 * - POST /v1/tenants/{tenantId}/users/invite - Invite user to tenant
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/users")
@Tag(name = "User Invitation", description = "Invite users to tenants")
@SecurityRequirement(name = "bearer-jwt")
@Slf4j
@RequiredArgsConstructor
public class UserInvitationController {

    private final UserInvitationService invitationService;

    /**
     * Invite user to tenant.
     *
     * Creates invitation with 48h expiration token.
     * Validates plan limits before sending.
     *
     * Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID
     * @param request Invitation details
     * @param jwt Authenticated user JWT
     * @return Invitation details with expiration
     */
    @PostMapping("/invite")
    @Operation(
        summary = "Invite user to tenant",
        description = "Send invitation email to new user. Token valid for 48 hours."
    )
    public ResponseEntity<InviteUserResponse> inviteUser(
        @PathVariable UUID tenantId,
        @Valid @RequestBody InviteUserRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        // Get resolved PostgreSQL usuario.id (NOT Keycloak UUID!)
        // This is resolved by TenantFilter via IdentityProviderMappingService
        UUID invitedBy = TenantContext.getUsuarioId();

        log.info("User {} inviting {} to tenant {}", invitedBy, request.getEmail(), tenantId);

        InviteUserResponse response = invitationService.inviteUser(tenantId, request, invitedBy);

        return ResponseEntity.ok(response);
    }
}
