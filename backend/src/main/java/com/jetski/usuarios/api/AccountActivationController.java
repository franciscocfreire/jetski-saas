package com.jetski.usuarios.api;

import com.jetski.usuarios.api.dto.CompleteActivationRequest;
import com.jetski.usuarios.api.dto.CompleteActivationResponse;
import com.jetski.usuarios.controller.dto.MagicActivationRequest;
import com.jetski.usuarios.internal.UserInvitationService;
import com.jetski.shared.security.MagicLinkTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller: Account Activation
 *
 * Public REST API for activating user accounts via invitation tokens.
 * Does NOT require authentication.
 *
 * Endpoints:
 * - POST /v1/auth/complete-activation - Complete activation with token + temporary password
 * - POST /v1/auth/magic-activate - Complete activation via magic link JWT (one-click UX)
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Account Activation", description = "Activate user account (public)")
@Slf4j
@RequiredArgsConstructor
public class AccountActivationController {

    private final UserInvitationService invitationService;
    private final MagicLinkTokenService magicLinkTokenService;

    /**
     * Complete account activation with temporary password (Option 2 flow).
     *
     * Public endpoint - no authentication required.
     * User provides invitation token AND temporary password (received in email) to complete activation.
     *
     * Steps:
     * 1. Validate token (not expired)
     * 2. Validate temporary password against BCrypt hash
     * 3. Create user in PostgreSQL with email_verified=true
     * 4. Create tenant membership
     * 5. Create user in Keycloak WITH temporary password + UPDATE_PASSWORD required action
     * 6. Mark invitation as activated
     * 7. User must change password on first login (Keycloak enforces)
     *
     * This flow ensures Keycloak manages password policies (complexity, length, history, etc).
     *
     * @param request Activation token and temporary password
     * @return Activated user details with success message
     */
    @PostMapping("/complete-activation")
    @Operation(
        summary = "Complete account activation with temporary password",
        description = "Complete activation: provide token and temporary password from email. Keycloak will force password change on first login. Public endpoint."
    )
    public ResponseEntity<CompleteActivationResponse> completeActivation(
        @Valid @RequestBody CompleteActivationRequest request
    ) {
        log.info("Complete activation request received (Option 2: temp password flow)");

        CompleteActivationResponse response = invitationService.completeActivation(request);

        log.info("Account activation completed successfully (Option 2): {}", response.getUsuarioId());

        return ResponseEntity.ok(response);
    }

    /**
     * Complete account activation via Magic Link JWT (one-click UX improvement).
     *
     * Public endpoint - no authentication required.
     * User clicks magic link in email → frontend extracts JWT → calls this endpoint.
     *
     * Steps:
     * 1. Validate JWT signature and expiration
     * 2. Extract invitation token + temporary password from JWT claims
     * 3. Call existing completeActivation() flow with extracted data
     * 4. Return activated user details
     *
     * This provides best UX: user only clicks link (no manual password entry required).
     * Security maintained: JWT is signed (cannot be forged) and contains encrypted temporary password.
     *
     * @param request Magic token (JWT) from URL
     * @return Activated user details with success message
     */
    @PostMapping("/magic-activate")
    @Operation(
        summary = "Complete account activation via magic link JWT",
        description = "One-click activation: provide magic token from email link. JWT contains encrypted invitation token + temporary password. Keycloak will force password change on first login. Public endpoint."
    )
    public ResponseEntity<CompleteActivationResponse> magicActivate(
        @Valid @RequestBody MagicActivationRequest request
    ) {
        log.info("Magic link activation request received");

        try {
            // 1. Validate JWT and extract claims (invitation token + temporary password)
            MagicLinkTokenService.MagicTokenClaims claims =
                magicLinkTokenService.validateAndParse(request.magicToken());

            log.info("Magic token validated successfully: invitationToken={}", claims.invitationToken());

            // 2. Build CompleteActivationRequest with extracted data
            CompleteActivationRequest activationRequest = new CompleteActivationRequest(
                claims.invitationToken(),
                claims.temporaryPassword()
            );

            // 3. Use existing activation flow
            CompleteActivationResponse response = invitationService.completeActivation(activationRequest);

            log.info("Magic link activation completed successfully: {}", response.getUsuarioId());

            return ResponseEntity.ok(response);

        } catch (MagicLinkTokenService.InvalidMagicTokenException e) {
            log.error("Invalid magic token: {}", e.getMessage());
            throw e;  // Will be handled by GlobalExceptionHandler
        }
    }
}
