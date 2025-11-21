package com.jetski.manutencao.internal.repository;

import com.jetski.manutencao.domain.OSManutencao;
import com.jetski.manutencao.domain.OSManutencaoStatus;
import com.jetski.manutencao.domain.OSManutencaoTipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository: OSManutencaoRepository
 *
 * <p>Handles database operations for maintenance orders (OS Manutenção).
 * RLS (Row Level Security) automatically filters queries by tenant_id.
 *
 * <h3>Core queries:</h3>
 * <ul>
 *   <li>List all orders (with filters: status, jetski, mechanic)</li>
 *   <li>Find active orders blocking a jetski</li>
 *   <li>Check jetski availability (no active OS)</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@Repository
public interface OSManutencaoRepository extends JpaRepository<OSManutencao, UUID> {

    /**
     * Find all orders for current tenant, ordered by newest first.
     * RLS policy automatically filters by tenant_id.
     *
     * @return List of all OSManutencao records
     */
    @Query("""
        SELECT os FROM OSManutencao os
        ORDER BY os.dtAbertura DESC
    """)
    List<OSManutencao> findAllByTenant();

    /**
     * Find all active orders (ABERTA, EM_ANDAMENTO, AGUARDANDO_PECAS).
     *
     * @return List of active OSManutencao records
     */
    @Query("""
        SELECT os FROM OSManutencao os
        WHERE os.status IN ('aberta', 'em_andamento', 'aguardando_pecas')
        ORDER BY os.prioridade DESC, os.dtAbertura ASC
    """)
    List<OSManutencao> findAllActive();

    /**
     * Find all orders for a specific jetski.
     * RLS ensures tenant isolation.
     *
     * @param jetskiId Jetski UUID
     * @return List of OSManutencao records for this jetski
     */
    @Query("""
        SELECT os FROM OSManutencao os
        WHERE os.jetskiId = :jetskiId
        ORDER BY os.dtAbertura DESC
    """)
    List<OSManutencao> findByJetskiId(@Param("jetskiId") UUID jetskiId);

    /**
     * Find all active orders for a specific jetski.
     * Business Rule: If any active OS exists, jetski is blocked from reservations.
     *
     * @param jetskiId Jetski UUID
     * @return List of active OSManutencao records blocking this jetski
     */
    @Query("""
        SELECT os FROM OSManutencao os
        WHERE os.jetskiId = :jetskiId
          AND os.status IN ('aberta', 'em_andamento', 'aguardando_pecas')
        ORDER BY os.prioridade DESC, os.dtAbertura ASC
    """)
    List<OSManutencao> findActiveByJetskiId(@Param("jetskiId") UUID jetskiId);

    /**
     * Find all orders assigned to a specific mechanic.
     *
     * @param mecanicoId Mechanic user UUID
     * @return List of OSManutencao records assigned to this mechanic
     */
    @Query("""
        SELECT os FROM OSManutencao os
        WHERE os.mecanicoId = :mecanicoId
        ORDER BY os.status ASC, os.prioridade DESC, os.dtAbertura ASC
    """)
    List<OSManutencao> findByMecanicoId(@Param("mecanicoId") UUID mecanicoId);

    /**
     * Find all orders by status.
     *
     * @param status OSManutencaoStatus
     * @return List of OSManutencao records with this status
     */
    @Query("""
        SELECT os FROM OSManutencao os
        WHERE os.status = :status
        ORDER BY os.prioridade DESC, os.dtAbertura ASC
    """)
    List<OSManutencao> findByStatus(@Param("status") OSManutencaoStatus status);

    /**
     * Find all orders by type.
     *
     * @param tipo OSManutencaoTipo (preventiva, corretiva, revisao)
     * @return List of OSManutencao records of this type
     */
    @Query("""
        SELECT os FROM OSManutencao os
        WHERE os.tipo = :tipo
        ORDER BY os.dtAbertura DESC
    """)
    List<OSManutencao> findByTipo(@Param("tipo") OSManutencaoTipo tipo);

    /**
     * Check if jetski has any active maintenance orders.
     * Business Rule RN06.1: Jetski with active OS cannot be reserved.
     *
     * @param jetskiId Jetski UUID
     * @return true if jetski has active OS (status ABERTA, EM_ANDAMENTO, AGUARDANDO_PECAS)
     */
    @Query("""
        SELECT CASE WHEN COUNT(os) > 0 THEN true ELSE false END
        FROM OSManutencao os
        WHERE os.jetskiId = :jetskiId
          AND os.status IN ('aberta', 'em_andamento', 'aguardando_pecas')
    """)
    boolean hasActiveMaintenanceByJetskiId(@Param("jetskiId") UUID jetskiId);

    /**
     * Count active orders in current tenant.
     *
     * @return Count of active OSManutencao records
     */
    @Query("""
        SELECT COUNT(os) FROM OSManutencao os
        WHERE os.status IN ('aberta', 'em_andamento', 'aguardando_pecas')
    """)
    long countActive();

    /**
     * Find by ID within current tenant.
     * RLS ensures tenant isolation.
     *
     * @param id OSManutencao UUID
     * @return Optional containing OSManutencao if found
     */
    Optional<OSManutencao> findById(UUID id);
}
