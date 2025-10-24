package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.JetskiStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository: JetskiRepository
 *
 * Handles database operations for individual jetski units.
 * RLS (Row Level Security) automatically filters queries by tenant_id.
 *
 * Core queries:
 * - List available jetskis for rental
 * - Find jetskis by model
 * - Check jetski availability (Business rule RN06)
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Repository
public interface JetskiRepository extends JpaRepository<Jetski, UUID> {

    /**
     * Find all active jetskis for current tenant.
     * RLS policy automatically filters by tenant_id.
     *
     * @return List of active Jetski records
     */
    @Query("""
        SELECT j FROM Jetski j
        WHERE j.ativo = true
        ORDER BY j.serie ASC
    """)
    List<Jetski> findAllActive();

    /**
     * Find all available jetskis for rental (RN06).
     * Business rule: Only DISPONIVEL jetskis can be reserved.
     *
     * @return List of available Jetski records
     */
    @Query("""
        SELECT j FROM Jetski j
        WHERE j.ativo = true
          AND j.status = :status
        ORDER BY j.serie ASC
    """)
    List<Jetski> findAllByStatus(@Param("status") JetskiStatus status);

    /**
     * Find all jetskis of a specific model.
     * Used for fleet management and model availability.
     *
     * @param modeloId Modelo UUID
     * @return List of Jetski records for this model
     */
    @Query("""
        SELECT j FROM Jetski j
        WHERE j.modeloId = :modeloId
          AND j.ativo = true
        ORDER BY j.serie ASC
    """)
    List<Jetski> findAllByModeloId(@Param("modeloId") UUID modeloId);

    /**
     * Find available jetskis of a specific model.
     * Used for reservation system.
     *
     * @param modeloId Modelo UUID
     * @return List of available Jetski records for this model
     */
    @Query("""
        SELECT j FROM Jetski j
        WHERE j.modeloId = :modeloId
          AND j.ativo = true
          AND j.status = 'DISPONIVEL'
        ORDER BY j.serie ASC
    """)
    List<Jetski> findAvailableByModeloId(@Param("modeloId") UUID modeloId);

    /**
     * Find jetski by serial number within current tenant.
     * Business rule: Serial numbers are unique per tenant.
     *
     * @param serie Serial number or registration plate
     * @return Optional containing Jetski if found
     */
    Optional<Jetski> findBySerie(String serie);

    /**
     * Check if serial number already exists in current tenant.
     *
     * @param serie Serial number
     * @return true if exists
     */
    boolean existsBySerie(String serie);

    /**
     * Count active jetskis in current tenant.
     * Used for plan limits and dashboard metrics.
     *
     * @return Count of active jetskis
     */
    @Query("""
        SELECT COUNT(j) FROM Jetski j
        WHERE j.ativo = true
    """)
    long countActive();

    /**
     * Count available jetskis for rental (RN06).
     *
     * @return Count of available jetskis
     */
    @Query("""
        SELECT COUNT(j) FROM Jetski j
        WHERE j.ativo = true
          AND j.status = 'DISPONIVEL'
    """)
    long countAvailable();

    /**
     * Count jetskis by modelo, active status, and jetski status.
     * Used for modelo-based availability checking (v0.3.0).
     *
     * @param modeloId Modelo UUID
     * @param ativo Whether jetski is active
     * @param status Jetski status (DISPONIVEL, LOCADO, MANUTENCAO, INDISPONIVEL)
     * @return Count of jetskis matching criteria
     */
    @Query("""
        SELECT COUNT(j) FROM Jetski j
        WHERE j.modeloId = :modeloId
          AND j.ativo = :ativo
          AND j.status = :status
    """)
    long countByModeloIdAndAtivoAndStatus(
        @Param("modeloId") UUID modeloId,
        @Param("ativo") Boolean ativo,
        @Param("status") JetskiStatus status
    );
}
