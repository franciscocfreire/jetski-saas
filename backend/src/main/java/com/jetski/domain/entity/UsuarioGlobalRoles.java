package com.jetski.domain.entity;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: UsuarioGlobalRoles (Global Platform Roles)
 *
 * Represents global platform-level roles for super admins.
 * Users with unrestricted_access=true can access ANY tenant.
 *
 * Use cases:
 * - Platform administrators (PLATFORM_ADMIN)
 * - Support team (SUPPORT)
 * - Auditors (AUDITOR)
 * - Network owners/franchisors managing 10,000+ tenants
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Entity
@Table(name = "usuario_global_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioGlobalRoles {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Type(StringArrayType.class)
    @Column(name = "roles", columnDefinition = "text[]", nullable = false)
    private String[] roles;

    /**
     * If true, user can access ANY tenant without explicit membership.
     * Use with extreme caution - intended for platform admins only.
     */
    @Column(name = "unrestricted_access", nullable = false)
    private Boolean unrestrictedAccess = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
