package com.jetski.usuarios.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: Member Activated
 *
 * <p>Published when an invited user completes their account activation.
 * This event is consumed by:
 * <ul>
 *   <li>AuditEventListener - to record the audit trail</li>
 *   <li>Future: Welcome notifications, onboarding workflows, etc.</li>
 * </ul>
 *
 * @param tenantId    The tenant where the member was activated
 * @param usuarioId   The unique identifier of the activated user
 * @param email       The email of the activated user
 * @param nome        The name of the activated user
 * @param papeis      The roles assigned to the user
 * @param occurredAt  When this event occurred
 *
 * @author Jetski Team
 * @since 0.10.0
 */
public record MemberActivatedEvent(
    UUID tenantId,
    UUID usuarioId,
    String email,
    String nome,
    String[] papeis,
    Instant occurredAt
) {

    /**
     * Factory method for creating a member activated event.
     */
    public static MemberActivatedEvent of(
            UUID tenantId,
            UUID usuarioId,
            String email,
            String nome,
            String[] papeis) {
        return new MemberActivatedEvent(
            tenantId, usuarioId, email, nome, papeis, Instant.now()
        );
    }
}
