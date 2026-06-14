package com.jetski.usuarios.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: Member Deactivated
 *
 * <p>Published when a member is deactivated from a tenant.
 * This event is consumed by:
 * <ul>
 *   <li>AuditEventListener - to record the audit trail</li>
 *   <li>Future: Session invalidation, notifications, etc.</li>
 * </ul>
 *
 * @param tenantId        The tenant where the member was deactivated
 * @param usuarioId       The user who was deactivated
 * @param email           The email of the deactivated user
 * @param deactivatedBy   The user who performed the deactivation
 * @param reason          The reason for deactivation (optional)
 * @param occurredAt      When this event occurred
 *
 * @author Jetski Team
 * @since 0.10.0
 */
public record MemberDeactivatedEvent(
    UUID tenantId,
    UUID usuarioId,
    String email,
    UUID deactivatedBy,
    String reason,
    Instant occurredAt
) {

    /**
     * Factory method for creating a member deactivated event.
     */
    public static MemberDeactivatedEvent of(
            UUID tenantId,
            UUID usuarioId,
            String email,
            UUID deactivatedBy,
            String reason) {
        return new MemberDeactivatedEvent(
            tenantId, usuarioId, email, deactivatedBy, reason, Instant.now()
        );
    }

    /**
     * Factory method for deactivation without reason.
     */
    public static MemberDeactivatedEvent of(
            UUID tenantId,
            UUID usuarioId,
            String email,
            UUID deactivatedBy) {
        return of(tenantId, usuarioId, email, deactivatedBy, null);
    }
}
