package com.jetski.shared.internal.keycloak;

import com.jetski.shared.security.UserProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Adapter that implements UserProvisioningService using Keycloak as the identity provider.
 *
 * <p>This adapter follows the Adapter pattern to decouple the public API
 * ({@link UserProvisioningService}) from the specific Keycloak implementation
 * ({@link KeycloakAdminService}).
 *
 * <p><strong>Benefits of this approach:</strong>
 * <ul>
 *   <li>Modules depend on interface, not implementation</li>
 *   <li>Easy to swap Keycloak for Auth0, Cognito, etc.</li>
 *   <li>Better testability (mock interface instead of concrete class)</li>
 *   <li>Respects Dependency Inversion Principle (DIP)</li>
 * </ul>
 *
 * <p><strong>Module Architecture:</strong><br>
 * This is an internal component of the 'shared' module. It is NOT exposed
 * as a public API. Other modules interact through {@link UserProvisioningService}.
 *
 * @author Jetski Team
 * @since 0.4.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
class KeycloakUserProvisioningAdapter implements UserProvisioningService {

    private final KeycloakAdminService keycloakAdminService;

    @Override
    public String provisionUserWithPassword(
        UUID usuarioId,
        String email,
        String nome,
        UUID tenantId,
        List<String> roles,
        String password
    ) {
        log.debug("Provisioning user with password via Keycloak adapter: email={}, tenant={}", email, tenantId);

        String keycloakUserId = keycloakAdminService.createUserWithPassword(
            usuarioId,
            email,
            nome,
            tenantId,
            roles,
            password
        );

        if (keycloakUserId != null) {
            log.info("User with password provisioned successfully: keycloakId={}, postgresId={}", keycloakUserId, usuarioId);
        } else {
            log.error("Failed to provision user with password: postgresId={}, email={}", usuarioId, email);
        }

        return keycloakUserId;
    }
}
