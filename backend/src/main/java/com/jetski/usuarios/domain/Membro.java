package com.jetski.usuarios.domain;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: Membro (Tenant Membership)
 *
 * Represents a user's membership in a tenant with specific roles.
 * This is the core table for multi-tenant access control.
 *
 * A user can be a member of thousands of tenants, each with different roles.
 * Example:
 * - João is GERENTE in Tenant A
 * - João is OPERADOR in Tenant B
 * - João is ADMIN_TENANT in Tenant C
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Entity
@Table(name = "membro")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Membro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Type(StringArrayType.class)
    @Column(name = "papeis", columnDefinition = "text[]", nullable = false)
    private String[] papeis;

    @Column(nullable = false)
    private Boolean ativo = true;

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
