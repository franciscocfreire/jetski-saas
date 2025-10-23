package com.jetski.usuarios.internal;

import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.exception.ConflictException;
import com.jetski.usuarios.domain.Usuario;
import com.jetski.usuarios.domain.UsuarioIdentityProvider;
import com.jetski.usuarios.internal.repository.UsuarioIdentityProviderRepository;
import com.jetski.usuarios.internal.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service: IdentityProviderMappingService
 *
 * Core service for managing identity provider mappings.
 * Resolves external provider IDs (Keycloak, Google, etc.) to internal user IDs.
 *
 * Key responsibilities:
 * - Resolve usuario_id from (provider, provider_user_id)
 * - Link new providers to existing users (account linking)
 * - Unlink providers from users
 * - List all providers linked to a user
 *
 * Performance:
 * - Results cached in Redis for 5 minutes
 * - Database queries use optimized composite indexes
 *
 * @author Jetski Team
 * @since 0.6.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityProviderMappingService {

    private final UsuarioIdentityProviderRepository mappingRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * Resolve internal user ID from provider credentials
     * Primary method for authentication flow
     *
     * @param provider Identity provider name (e.g., 'keycloak', 'google')
     * @param providerUserId External user ID from provider (from JWT sub claim)
     * @return Internal user UUID
     * @throws NotFoundException if mapping doesn't exist
     */
    @Cacheable(
        value = "identity-provider-mapping",
        key = "#provider + ':' + #providerUserId",
        unless = "#result == null"
    )
    @Transactional(readOnly = true)
    public UUID resolveUsuarioId(String provider, String providerUserId) {
        log.debug("Resolving usuario_id: provider={}, providerUserId={}", provider, providerUserId);

        Optional<UsuarioIdentityProvider> mapping =
            mappingRepository.findByProviderAndProviderUserId(provider, providerUserId);

        if (mapping.isEmpty()) {
            log.warn("Identity provider mapping not found: provider={}, providerUserId={}",
                provider, providerUserId);
            throw new NotFoundException(
                String.format("User not found for provider '%s' with ID '%s'", provider, providerUserId)
            );
        }

        UUID usuarioId = mapping.get().getUsuario().getId();
        log.debug("Resolved usuario_id={} for provider={}, providerUserId={}",
            usuarioId, provider, providerUserId);

        return usuarioId;
    }

    /**
     * Link a new identity provider to an existing user
     * Used for account linking (e.g., link Google to existing Keycloak account)
     *
     * @param usuarioId Internal user ID
     * @param provider Provider name
     * @param providerUserId External provider user ID
     * @return Created mapping
     * @throws NotFoundException if user doesn't exist
     * @throws ConflictException if provider already linked to another user
     */
    @CacheEvict(value = "identity-provider-mapping", key = "#provider + ':' + #providerUserId")
    @Transactional
    public UsuarioIdentityProvider linkProvider(UUID usuarioId, String provider, String providerUserId) {
        log.info("Linking provider: usuarioId={}, provider={}, providerUserId={}",
            usuarioId, provider, providerUserId);

        // Validate user exists
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new NotFoundException("User not found: " + usuarioId));

        // Check if provider already linked to another user
        Optional<UsuarioIdentityProvider> existing =
            mappingRepository.findByProviderAndProviderUserId(provider, providerUserId);

        if (existing.isPresent()) {
            UUID existingUsuarioId = existing.get().getUsuario().getId();
            if (!existingUsuarioId.equals(usuarioId)) {
                log.warn("Provider already linked to different user: provider={}, providerUserId={}, existingUser={}",
                    provider, providerUserId, existingUsuarioId);
                throw new ConflictException(
                    String.format("Provider '%s' with ID '%s' is already linked to another account", provider, providerUserId)
                );
            }
            // Already linked to same user - return existing
            log.info("Provider already linked to this user - returning existing mapping");
            return existing.get();
        }

        // Create new mapping
        UsuarioIdentityProvider mapping = UsuarioIdentityProvider.link(usuario, provider, providerUserId);
        UsuarioIdentityProvider saved = mappingRepository.save(mapping);

        log.info("Provider linked successfully: id={}, usuarioId={}, provider={}",
            saved.getId(), usuarioId, provider);

        return saved;
    }

    /**
     * Unlink a provider from a user
     * Used for account unlinking (e.g., remove Google login)
     *
     * Note: Cannot unlink last provider (user would lose access)
     *
     * @param usuarioId Internal user ID
     * @param provider Provider name
     * @throws NotFoundException if mapping doesn't exist
     * @throws ConflictException if trying to unlink last provider
     */
    @CacheEvict(value = "identity-provider-mapping", allEntries = true)
    @Transactional
    public void unlinkProvider(UUID usuarioId, String provider) {
        log.info("Unlinking provider: usuarioId={}, provider={}", usuarioId, provider);

        // Check if mapping exists
        Optional<UsuarioIdentityProvider> mapping =
            mappingRepository.findByUsuarioIdAndProvider(usuarioId, provider);

        if (mapping.isEmpty()) {
            throw new NotFoundException(
                String.format("Provider '%s' not linked to user %s", provider, usuarioId)
            );
        }

        // Prevent unlinking last provider
        long providerCount = mappingRepository.countByUsuarioId(usuarioId);
        if (providerCount <= 1) {
            log.warn("Cannot unlink last provider: usuarioId={}, provider={}", usuarioId, provider);
            throw new ConflictException(
                "Cannot unlink last identity provider. User would lose access to the account."
            );
        }

        // Delete mapping
        mappingRepository.deleteByUsuarioIdAndProvider(usuarioId, provider);

        log.info("Provider unlinked successfully: usuarioId={}, provider={}", usuarioId, provider);
    }

    /**
     * List all identity providers linked to a user
     * Used for account management UI
     *
     * @param usuarioId Internal user ID
     * @return List of provider names
     */
    @Transactional(readOnly = true)
    public List<String> listLinkedProviders(UUID usuarioId) {
        log.debug("Listing linked providers: usuarioId={}", usuarioId);

        List<UsuarioIdentityProvider> mappings = mappingRepository.findAllByUsuarioId(usuarioId);

        List<String> providers = mappings.stream()
            .map(UsuarioIdentityProvider::getProvider)
            .collect(Collectors.toList());

        log.debug("Found {} linked providers for usuarioId={}: {}",
            providers.size(), usuarioId, providers);

        return providers;
    }

    /**
     * Get detailed mapping information for all providers linked to a user
     * Used for admin UI and debugging
     *
     * @param usuarioId Internal user ID
     * @return List of all mappings
     */
    @Transactional(readOnly = true)
    public List<UsuarioIdentityProvider> getDetailedMappings(UUID usuarioId) {
        return mappingRepository.findAllByUsuarioId(usuarioId);
    }

    /**
     * Check if a provider is already linked (to any user)
     * Used for duplicate prevention during account creation
     *
     * @param provider Provider name
     * @param providerUserId External user ID
     * @return true if provider is linked
     */
    @Transactional(readOnly = true)
    public boolean isProviderLinked(String provider, String providerUserId) {
        return mappingRepository.existsByProviderAndProviderUserId(provider, providerUserId);
    }
}
