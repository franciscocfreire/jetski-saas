package com.jetski.usuarios.internal.repository;

import com.jetski.usuarios.domain.UsuarioIdentityProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository: UsuarioIdentityProviderRepository
 *
 * Data access for identity provider mappings.
 * Used to resolve external provider IDs to internal user IDs.
 *
 * @author Jetski Team
 * @since 0.6.0
 */
@Repository
public interface UsuarioIdentityProviderRepository extends JpaRepository<UsuarioIdentityProvider, UUID> {

    /**
     * Find mapping by provider and external user ID
     * Primary method for resolving user identity from JWT
     *
     * @param provider Identity provider name (e.g., 'keycloak', 'google')
     * @param providerUserId External user ID from provider
     * @return Mapping if exists
     */
    Optional<UsuarioIdentityProvider> findByProviderAndProviderUserId(String provider, String providerUserId);

    /**
     * Check if provider mapping exists
     * Used to prevent duplicate account linking
     *
     * @param provider Identity provider name
     * @param providerUserId External user ID
     * @return true if mapping exists
     */
    boolean existsByProviderAndProviderUserId(String provider, String providerUserId);

    /**
     * List all identity providers linked to a user
     * Used for account management UI (show linked accounts)
     *
     * @param usuarioId Internal user ID
     * @return List of all provider mappings for this user
     */
    @Query("SELECT uip FROM UsuarioIdentityProvider uip WHERE uip.usuario.id = :usuarioId ORDER BY uip.linkedAt DESC")
    List<UsuarioIdentityProvider> findAllByUsuarioId(@Param("usuarioId") UUID usuarioId);

    /**
     * Count providers linked to user
     * Quick check for account linking limits
     *
     * @param usuarioId Internal user ID
     * @return Number of linked providers
     */
    @Query("SELECT COUNT(uip) FROM UsuarioIdentityProvider uip WHERE uip.usuario.id = :usuarioId")
    long countByUsuarioId(@Param("usuarioId") UUID usuarioId);

    /**
     * Find mapping by user and provider
     * Used for unlinking specific provider
     *
     * @param usuarioId Internal user ID
     * @param provider Provider name
     * @return Mapping if exists
     */
    @Query("SELECT uip FROM UsuarioIdentityProvider uip WHERE uip.usuario.id = :usuarioId AND uip.provider = :provider")
    Optional<UsuarioIdentityProvider> findByUsuarioIdAndProvider(
        @Param("usuarioId") UUID usuarioId,
        @Param("provider") String provider
    );

    /**
     * Delete mapping by user and provider
     * Used for account unlinking
     *
     * @param usuarioId Internal user ID
     * @param provider Provider name
     */
    void deleteByUsuarioIdAndProvider(UUID usuarioId, String provider);
}
