package com.jetski.usuarios.internal.repository;

import com.jetski.usuarios.domain.Convite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Convite entity.
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Repository
public interface ConviteRepository extends JpaRepository<Convite, UUID> {

    /**
     * Find invitation by token (for activation).
     */
    Optional<Convite> findByTokenAndStatus(String token, Convite.ConviteStatus status);

    /**
     * Find invitation by token (any status).
     */
    Optional<Convite> findByToken(String token);

    /**
     * Find pending invitations for a tenant.
     */
    List<Convite> findByTenantIdAndStatus(UUID tenantId, Convite.ConviteStatus status);

    /**
     * Check if email already has pending invitation in tenant.
     */
    boolean existsByTenantIdAndEmailAndStatus(UUID tenantId, String email, Convite.ConviteStatus status);

    /**
     * Find all expired invitations (status=PENDING and expires_at < now).
     */
    @Query("SELECT c FROM Convite c WHERE c.status = 'PENDING' AND c.expiresAt < :now")
    List<Convite> findExpiredInvitations(@Param("now") Instant now);

    /**
     * Count pending invitations for a tenant.
     */
    long countByTenantIdAndStatus(UUID tenantId, Convite.ConviteStatus status);

    /**
     * Find invitation by tenant and email (any status).
     */
    Optional<Convite> findByTenantIdAndEmail(UUID tenantId, String email);
}
