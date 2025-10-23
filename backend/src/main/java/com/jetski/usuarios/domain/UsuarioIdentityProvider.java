package com.jetski.usuarios.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity: UsuarioIdentityProvider
 *
 * Maps internal user UUIDs to external identity provider IDs.
 * Enables:
 * - Multiple identity providers per user (account linking)
 * - Provider migration without data loss
 * - Audit trail of login methods
 * - Decoupling from specific OAuth2/OIDC providers
 *
 * Example scenarios:
 * - User logs in with Keycloak (provider='keycloak', provider_user_id=kc-uuid)
 * - User links Google account (provider='google', provider_user_id=google-sub)
 * - Migrate from Keycloak to Auth0 (keep both mappings during transition)
 *
 * @author Jetski Team
 * @since 0.6.0
 */
@Entity
@Table(
    name = "usuario_identity_provider",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_usuario_identity_provider_provider_user",
            columnNames = {"provider", "provider_user_id"}
        )
    },
    indexes = {
        @Index(name = "idx_usuario_identity_provider_usuario_id", columnList = "usuario_id"),
        @Index(name = "idx_usuario_identity_provider_provider_user", columnList = "provider, provider_user_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = "usuario")
public class UsuarioIdentityProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * Internal user ID (PostgreSQL)
     * Source of truth for user identity
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @ForeignKey(name = "fk_usuario_identity_provider_usuario"))
    private Usuario usuario;

    /**
     * Identity provider name
     * Examples: 'keycloak', 'google', 'azure-ad', 'auth0', 'github'
     */
    @Column(nullable = false, length = 50)
    private String provider;

    /**
     * External user ID from the identity provider
     * Can be UUID (Keycloak), sub claim (Google), etc.
     */
    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    /**
     * Timestamp when this provider was linked to the user
     * Useful for account linking auditing
     */
    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Factory method: Create mapping for new provider link
     */
    public static UsuarioIdentityProvider link(Usuario usuario, String provider, String providerUserId) {
        return UsuarioIdentityProvider.builder()
            .usuario(usuario)
            .provider(provider)
            .providerUserId(providerUserId)
            .linkedAt(LocalDateTime.now())
            .build();
    }
}
