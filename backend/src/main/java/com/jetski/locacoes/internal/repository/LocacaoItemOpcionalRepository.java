package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.LocacaoItemOpcional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository: LocacaoItemOpcionalRepository
 *
 * Handles database operations for rental optional items (LocacaoItemOpcional).
 * RLS (Row Level Security) automatically filters queries by tenant_id.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Repository
public interface LocacaoItemOpcionalRepository extends JpaRepository<LocacaoItemOpcional, UUID> {

    /**
     * Find all optional items for a specific rental.
     *
     * @param locacaoId Rental UUID
     * @return List of LocacaoItemOpcional records
     */
    List<LocacaoItemOpcional> findByLocacaoIdOrderByCreatedAtAsc(UUID locacaoId);

    /**
     * Find all optional items for a rental within tenant.
     *
     * @param tenantId Tenant UUID
     * @param locacaoId Rental UUID
     * @return List of LocacaoItemOpcional records
     */
    List<LocacaoItemOpcional> findByTenantIdAndLocacaoIdOrderByCreatedAtAsc(UUID tenantId, UUID locacaoId);

    /**
     * Find specific item by tenant, rental, and item ID.
     *
     * @param tenantId Tenant UUID
     * @param locacaoId Rental UUID
     * @param id Item UUID
     * @return Optional containing LocacaoItemOpcional if found
     */
    Optional<LocacaoItemOpcional> findByTenantIdAndLocacaoIdAndId(UUID tenantId, UUID locacaoId, UUID id);

    /**
     * Check if an optional item is already added to a rental.
     *
     * @param locacaoId Rental UUID
     * @param itemOpcionalId Optional item UUID
     * @return true if already added
     */
    boolean existsByLocacaoIdAndItemOpcionalId(UUID locacaoId, UUID itemOpcionalId);

    /**
     * Calculate total value of all optional items for a rental.
     *
     * @param locacaoId Rental UUID
     * @return Sum of valorCobrado, or 0 if no items
     */
    @Query("""
        SELECT COALESCE(SUM(i.valorCobrado), 0)
        FROM LocacaoItemOpcional i
        WHERE i.locacaoId = :locacaoId
    """)
    BigDecimal sumValorCobradoByLocacaoId(@Param("locacaoId") UUID locacaoId);

    /**
     * Count optional items for a rental.
     *
     * @param locacaoId Rental UUID
     * @return Count of items
     */
    long countByLocacaoId(UUID locacaoId);

    /**
     * Delete all optional items for a rental.
     *
     * @param locacaoId Rental UUID
     */
    void deleteByLocacaoId(UUID locacaoId);
}
