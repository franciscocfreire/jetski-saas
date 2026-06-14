package com.jetski.usuarios.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: Member Invited
 *
 * <p>Published when a new user is invited to join a tenant.
 * This event is consumed by:
 * <ul>
 *   <li>AuditEventListener - to record the audit trail</li>
 *   <li>Future: Analytics, notifications, etc.</li>
 * </ul>
 *
 * @param tenantId    The tenant to which the user is being invited
 * @param conviteId   The unique identifier of the invitation
 * @param email       The email of the invited user
 * @param nome        The name of the invited user
 * @param papeis      The roles being assigned to the user
 * @param invitedBy   The user who sent the invitation
 * @param occurredAt  When this event occurred
 *
 * @author Jetski Team
 * @since 0.10.0
 */
public record MemberInvitedEvent(
    UUID tenantId,
    UUID conviteId,
    String email,
    String nome,
    String[] papeis,
    UUID invitedBy,
    Instant occurredAt
) {

    /**
     * Factory method for creating a member invited event.
     */
    public static MemberInvitedEvent of(
            UUID tenantId,
            UUID conviteId,
            String email,
            String nome,
            String[] papeis,
            UUID invitedBy) {
        return new MemberInvitedEvent(
            tenantId, conviteId, email, nome, papeis, invitedBy, Instant.now()
        );
    }
}
