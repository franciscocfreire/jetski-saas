package com.jetski.shared.internal.keycloak;

import com.jetski.shared.security.IdentityConflictException;
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
    private final KeycloakPasswordValidator keycloakPasswordValidator;

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

    @Override
    public ClienteProvisionResult provisionOrReuseCliente(
        UUID clienteId,
        String email,
        String nome,
        UUID tenantId,
        String senhaTemporaria
    ) {
        String existingId = keycloakAdminService.findUserIdByEmail(email);
        if (existingId == null) {
            String created = keycloakAdminService.createUserWithPassword(
                clienteId, email, nome, tenantId, List.of("CLIENTE"), senhaTemporaria);
            return created == null ? null : new ClienteProvisionResult(created, false);
        }

        // Populações staff × cliente nunca se cruzam: só reutiliza identidade CLIENTE.
        if (!keycloakAdminService.userHasRealmRole(existingId, "CLIENTE")) {
            log.warn("Claim recusado: e-mail já pertence a identidade não-cliente (keycloakId={})", existingId);
            throw new IdentityConflictException(
                "E-mail já pertence a uma conta que não é de cliente");
        }

        // Reuso (ex.: auto-cadastro no portal): senha/username/atributos intactos —
        // cliente é multi-loja, o vínculo por loja fica em cliente_identity_provider.
        keycloakAdminService.marcarEmailVerificado(existingId);
        log.info("Identidade CLIENTE existente reutilizada no claim: keycloakId={}", existingId);
        return new ClienteProvisionResult(existingId, true);
    }

    @Override
    public String provisionCustomer(String email, String nome, String senha) {
        log.debug("Provisioning self-registered customer via Keycloak adapter: email={}", email);
        return keycloakAdminService.createCustomerUser(email, nome, senha);
    }

    @Override
    public boolean updateUserName(String providerUserId, String nome) {
        return keycloakAdminService.updateUserName(providerUserId, nome);
    }

    @Override
    public boolean definirCpf(String providerUserId, String cpfDigits) {
        return keycloakAdminService.definirCpf(providerUserId, cpfDigits);
    }

    @Override
    public String findEmailById(String providerUserId) {
        return keycloakAdminService.findEmailById(providerUserId);
    }

    @Override
    public String findUserIdByUsername(String username) {
        return keycloakAdminService.findUserIdByUsername(username);
    }

    @Override
    public com.jetski.shared.security.FederatedIdentity findFederatedIdentity(
            String providerUserId, String idpAlias) {
        return keycloakAdminService.findFederatedIdentity(providerUserId, idpAlias);
    }

    @Override
    public boolean transferFederatedIdentity(
            String fromProviderUserId, String toProviderUserId, String idpAlias) {
        return keycloakAdminService.transferFederatedIdentity(
            fromProviderUserId, toProviderUserId, idpAlias);
    }

    @Override
    public boolean deleteUser(String providerUserId) {
        return keycloakAdminService.deleteUser(providerUserId);
    }

    @Override
    public PasswordCheck validatePassword(String username, String password) {
        return keycloakPasswordValidator.validatePassword(username, password);
    }

    @Override
    public boolean hasPasswordCredential(String providerUserId) {
        return keycloakAdminService.hasPasswordCredential(providerUserId);
    }

    @Override
    public boolean resetPassword(String providerUserId, String novaSenha) {
        return keycloakAdminService.resetPassword(providerUserId, novaSenha);
    }
}
