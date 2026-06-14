package com.jetski.usuarios.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: Member Roles Changed
 *
 * <p>Published when a member's roles are updated.
 * This event is consumed by:
 * <ul>
 *   <li>AuditEventListener - to record the audit trail with before/after state</li>
 *   <li>Future: Permission cache invalidation, notifications, etc.</li>
 * </ul>
 *
 * @param tenantId       The tenant where the roles were changed
 * @param usuarioId      The user whose roles were changed
 * @param email          The email of the user
 * @param previousRoles  The roles before the change
 * @param newRoles       The roles after the change
 * @param changedBy      The user who made the change
 * @param occurredAt     When this event occurred
 *
 * @author Jetski Team
 * @since 0.10.0
 */
public record MemberRolesChangedEvent(
    UUID tenantId,
    UUID usuarioId,
    String email,
    String[] previousRoles,
    String[] newRoles,
    UUID changedBy,
    Instant occurredAt
) {

    /**
     * Factory method for creating a member roles changed event.
     */
    public static MemberRolesChangedEvent of(
            UUID tenantId,
            UUID usuarioId,
            String email,
            String[] previousRoles,
            String[] newRoles,
            UUID changedBy) {
        return new MemberRolesChangedEvent(
            tenantId, usuarioId, email, previousRoles, newRoles, changedBy, Instant.now()
        );
    }
}
