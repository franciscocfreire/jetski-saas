package com.jetski.signup.api;

import com.jetski.shared.security.MagicLinkTokenService;
import com.jetski.signup.api.dto.CreateTenantRequest;
import com.jetski.signup.api.dto.TenantSignupRequest;
import com.jetski.signup.api.dto.TenantSignupResponse;
import com.jetski.signup.internal.TenantSignupService;
import com.jetski.usuarios.controller.dto.MagicActivationRequest;
import com.jetski.usuarios.internal.IdentityProviderMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Controller: Tenant Signup
 *
 * Handles tenant self-service signup operations:
 * - Public signup for new users
 * - Authenticated tenant creation for existing users
 * - Signup activation
 *
 * @author Jetski Team
 * @since 0.5.0
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Signup", description = "Tenant signup and registration")
public class TenantSignupController {

    private final TenantSignupService signupService;
    private final IdentityProviderMappingService identityMappingService;
    private final MagicLinkTokenService magicLinkTokenService;

    /**
     * POST /v1/signup/tenant
     *
     * Public endpoint for new users to create a tenant and admin account.
     * User will receive an activation email with temporary password.
     *
     * @param request Tenant and admin user details
     * @return TenantSignupResponse with success info
     */
    @PostMapping("/signup/tenant")
    @Operation(
        summary = "Signup new tenant (public)",
        description = "Creates a new tenant and admin user account. " +
            "User will receive an activation email to complete registration."
    )
    public ResponseEntity<TenantSignupResponse> signupTenant(
        @RequestBody @Valid TenantSignupRequest request
    ) {
        log.info("Tenant signup request for: {}", request.adminEmail());
        TenantSignupResponse response = signupService.signupNewTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /v1/tenants/create
     *
     * Authenticated endpoint for existing users to create a new tenant.
     * User is immediately added as ADMIN_TENANT of the new company.
     *
     * @param request Tenant details
     * @param jwt Authenticated user JWT
     * @return TenantSignupResponse with success info
     */
    @PostMapping("/tenants/create")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Create new tenant (authenticated)",
        description = "Creates a new tenant for an existing user. " +
            "User is immediately added as admin of the new company.",
        security = @SecurityRequirement(name = "bearer-jwt")
    )
    public ResponseEntity<TenantSignupResponse> createTenant(
        @RequestBody @Valid CreateTenantRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID usuarioId = extractUsuarioId(jwt);
        log.info("Create tenant request for user: {} ({})", usuarioId, request.razaoSocial());
        TenantSignupResponse response = signupService.createTenantForExistingUser(request, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /v1/signup/check-slug
     *
     * Public endpoint to check if a slug is available.
     *
     * @param slug The slug to check
     * @return Map with "available" boolean
     */
    @GetMapping("/signup/check-slug")
    @Operation(
        summary = "Check slug availability",
        description = "Checks if a slug (company URL identifier) is available for use."
    )
    public ResponseEntity<Map<String, Boolean>> checkSlug(
        @RequestParam String slug
    ) {
        boolean available = signupService.isSlugAvailable(slug);
        return ResponseEntity.ok(Map.of("available", available));
    }

    /**
     * POST /v1/signup/activate
     *
     * Public endpoint to complete signup activation.
     * Creates the user account in PostgreSQL and Keycloak.
     *
     * @param token Activation token from email
     * @param temporaryPassword Temporary password from email
     * @return Success message
     */
    @PostMapping("/signup/activate")
    @Operation(
        summary = "Activate signup",
        description = "Completes the signup activation by creating user account. " +
            "User should change password on first login."
    )
    public ResponseEntity<Map<String, String>> activateSignup(
        @RequestParam String token,
        @RequestParam String temporaryPassword
    ) {
        log.info("Signup activation request");
        signupService.completeSignupActivation(token, temporaryPassword);
        return ResponseEntity.ok(Map.of(
            "message", "Conta ativada com sucesso! Você já pode fazer login."
        ));
    }

    /**
     * POST /v1/signup/magic-activate
     *
     * Public endpoint to complete signup activation via magic link JWT.
     * Provides one-click activation UX - user only clicks the link in email.
     *
     * @param request Magic token (JWT) from URL
     * @return Success message
     */
    @PostMapping("/signup/magic-activate")
    @Operation(
        summary = "Activate signup via magic link",
        description = "Completes the signup activation using magic link JWT. " +
            "One-click activation: JWT contains encrypted token + password."
    )
    public ResponseEntity<Map<String, String>> magicActivateSignup(
        @RequestBody @Valid MagicActivationRequest request
    ) {
        log.info("Signup magic activation request received");

        try {
            // 1. Validate JWT and extract claims
            MagicLinkTokenService.MagicTokenClaims claims =
                magicLinkTokenService.validateAndParse(request.magicToken());

            log.info("Magic token validated for signup activation");

            // 2. Complete activation with extracted data
            signupService.completeSignupActivation(
                claims.invitationToken(),
                claims.temporaryPassword()
            );

            log.info("Signup magic activation completed successfully");

            return ResponseEntity.ok(Map.of(
                "message", "Conta ativada com sucesso! Você já pode fazer login."
            ));

        } catch (MagicLinkTokenService.InvalidMagicTokenException e) {
            log.error("Invalid magic token for signup: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Extract usuario ID from JWT token.
     * Uses email as fallback if sub claim is not available.
     */
    private UUID extractUsuarioId(Jwt jwt) {
        // Try to get provider user ID from subject
        String providerUserId = jwt.getSubject();

        // Fallback: use email to resolve usuario ID
        if (providerUserId == null && jwt.hasClaim("email")) {
            String email = jwt.getClaimAsString("email");
            log.debug("JWT without 'sub' claim, using email fallback: {}", email);
            return identityMappingService.resolveUsuarioIdByEmail(email);
        }

        if (providerUserId == null) {
            throw new RuntimeException("JWT sem 'sub' nem 'email' claim. Impossível identificar usuário.");
        }

        return identityMappingService.resolveUsuarioId("keycloak", providerUserId);
    }
}
