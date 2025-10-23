package com.jetski.shared.security;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for user provisioning in identity provider.
 *
 * <p>This interface abstracts the identity provider implementation (Keycloak, Auth0, Cognito, etc.)
 * allowing the usuarios module to provision users without depending on specific provider details.
 *
 * <p><strong>Implementation details (Option 2 flow):</strong>
 * <ul>
 *   <li>User is created WITH temporary password</li>
 *   <li>Email is VERIFIED (invitation validates email)</li>
 *   <li>User must change password on first login (UPDATE_PASSWORD required action)</li>
 *   <li>Roles are assigned during provisioning</li>
 *   <li>Tenant ID is stored as custom attribute</li>
 * </ul>
 *
 * <p><strong>Module Architecture:</strong><br>
 * This is a public API of the 'shared::security' module. Other modules can depend on
 * this interface without coupling to specific identity provider implementations.
 *
 * @author Jetski Team
 * @since 0.4.0
 * @see com.jetski.shared.internal.keycloak.KeycloakUserProvisioningAdapter
 */
public interface UserProvisioningService {

    /**
     * Provisions a new user in the identity provider WITH TEMPORARY PASSWORD (Option 2 flow).
     *
     * <p><strong>Flow (single-step activation with temp password):</strong>
     * <ol>
     *   <li>Create user account in identity provider</li>
     *   <li>Set email as username</li>
     *   <li>Mark email as VERIFIED (invitation flow validates email)</li>
     *   <li>Set TEMPORARY password (validated against BCrypt hash)</li>
     *   <li>Add required action: UPDATE_PASSWORD (force password change on first login)</li>
     *   <li>Add custom attributes (tenant_id, postgresql_user_id)</li>
     *   <li>Assign realm-level roles</li>
     * </ol>
     *
     * <p><strong>Use case:</strong><br>
     * This method is used for single-email invitation flow where the backend generates
     * a random temporary password, stores its BCrypt hash, sends it in the invitation email,
     * and the user activates their account using token + temporary password. On first login,
     * Keycloak forces the user to change their password according to configured password policies.
     *
     * <p><strong>Security considerations:</strong>
     * <ul>
     *   <li>User is created in ENABLED state (ready for immediate login)</li>
     *   <li>Password is set as TEMPORARY (Keycloak enforces change on first login)</li>
     *   <li>Email is marked as verified (invitation validates email)</li>
     *   <li>Password policies are managed by Keycloak (length, complexity, history, etc)</li>
     * </ul>
     *
     * @param usuarioId UUID of the user in PostgreSQL (stored as custom attribute)
     * @param email User email address (used as username)
     * @param nome Full name of the user
     * @param tenantId Tenant ID (stored as custom attribute for multi-tenancy)
     * @param roles List of role names to assign to the user
     * @param password TEMPORARY password (validated against BCrypt hash before calling)
     * @return Provider user ID (UUID string) if successful, null otherwise
     */
    String provisionUserWithPassword(
        UUID usuarioId,
        String email,
        String nome,
        UUID tenantId,
        List<String> roles,
        String password
    );
}
