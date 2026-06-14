package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.PresencaVendedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository: PresencaVendedorRepository
 *
 * Handles database operations for seller daily attendance (PresencaVendedor).
 * RLS (Row Level Security) automatically filters queries by tenant_id.
 *
 * Core queries:
 * - List attendance by date
 * - Find attendance by seller and date
 * - Sum daily attendance values for closing
 *
 * @author Jetski Team
 * @since 0.10.0
 */
@Repository
public interface PresencaVendedorRepository extends JpaRepository<PresencaVendedor, UUID> {

    /**
     * Find all attendance records for a specific date.
     * RLS policy automatically filters by tenant_id.
     *
     * @param dtReferencia Reference date
     * @return List of PresencaVendedor records
     */
    @Query("""
        SELECT p FROM PresencaVendedor p
        JOIN FETCH p.vendedor
        WHERE p.dtReferencia = :dtReferencia
        ORDER BY p.vendedor.nome ASC
    """)
    List<PresencaVendedor> findAllByDtReferencia(@Param("dtReferencia") LocalDate dtReferencia);

    /**
     * Find attendance record for a specific seller on a specific date.
     *
     * @param vendedorId Seller UUID
     * @param dtReferencia Reference date
     * @return Optional containing PresencaVendedor if found
     */
    Optional<PresencaVendedor> findByVendedorIdAndDtReferencia(UUID vendedorId, LocalDate dtReferencia);

    /**
     * Check if attendance already exists for seller on date.
     *
     * @param vendedorId Seller UUID
     * @param dtReferencia Reference date
     * @return true if exists
     */
    boolean existsByVendedorIdAndDtReferencia(UUID vendedorId, LocalDate dtReferencia);

    /**
     * Sum total effective attendance values for a date.
     * Used for daily closing consolidation.
     *
     * @param tenantId Tenant UUID
     * @param dtReferencia Reference date
     * @return Total attendance value (sum of valor_ajustado or valor_diaria)
     */
    @Query(value = """
        SELECT COALESCE(SUM(
            CASE WHEN valor_ajustado IS NOT NULL THEN valor_ajustado
                 ELSE valor_diaria END
        ), 0)
        FROM presenca_vendedor
        WHERE tenant_id = :tenantId
          AND dt_referencia = :dtReferencia
    """, nativeQuery = true)
    BigDecimal sumTotalDiariasByDate(@Param("tenantId") UUID tenantId, @Param("dtReferencia") LocalDate dtReferencia);

    /**
     * Count attendance records by date.
     *
     * @param dtReferencia Reference date
     * @return Count of records
     */
    @Query("""
        SELECT COUNT(p) FROM PresencaVendedor p
        WHERE p.dtReferencia = :dtReferencia
    """)
    int countByDtReferencia(@Param("dtReferencia") LocalDate dtReferencia);

    /**
     * Count full-day attendance records by date.
     *
     * @param dtReferencia Reference date
     * @return Count of INTEGRAL records
     */
    @Query("""
        SELECT COUNT(p) FROM PresencaVendedor p
        WHERE p.dtReferencia = :dtReferencia
          AND p.tipo = com.jetski.locacoes.domain.TipoPresenca.INTEGRAL
    """)
    int countIntegralByDtReferencia(@Param("dtReferencia") LocalDate dtReferencia);

    /**
     * Count half-day attendance records by date.
     *
     * @param dtReferencia Reference date
     * @return Count of MEIA_DIARIA records
     */
    @Query("""
        SELECT COUNT(p) FROM PresencaVendedor p
        WHERE p.dtReferencia = :dtReferencia
          AND p.tipo = com.jetski.locacoes.domain.TipoPresenca.MEIA_DIARIA
    """)
    int countMeiaDiariaByDtReferencia(@Param("dtReferencia") LocalDate dtReferencia);

    /**
     * Delete all attendance records for a specific date.
     * Used when re-registering attendance for a day.
     *
     * @param dtReferencia Reference date
     */
    void deleteAllByDtReferencia(LocalDate dtReferencia);

    /**
     * List attendance records for a seller in a date range.
     *
     * @param vendedorId Seller UUID
     * @param dtInicio Start date (inclusive)
     * @param dtFim End date (inclusive)
     * @return List of PresencaVendedor records
     */
    @Query("""
        SELECT p FROM PresencaVendedor p
        WHERE p.vendedorId = :vendedorId
          AND p.dtReferencia >= :dtInicio
          AND p.dtReferencia <= :dtFim
        ORDER BY p.dtReferencia DESC
    """)
    List<PresencaVendedor> findByVendedorIdAndPeriodo(
            @Param("vendedorId") UUID vendedorId,
            @Param("dtInicio") LocalDate dtInicio,
            @Param("dtFim") LocalDate dtFim);

    /**
     * Sum total effective attendance values for a seller in a period.
     *
     * @param vendedorId Seller UUID
     * @param dtInicio Start date (inclusive)
     * @param dtFim End date (inclusive)
     * @return Total attendance value
     */
    @Query(value = """
        SELECT COALESCE(SUM(
            CASE WHEN valor_ajustado IS NOT NULL THEN valor_ajustado
                 ELSE valor_diaria END
        ), 0)
        FROM presenca_vendedor
        WHERE vendedor_id = :vendedorId
          AND dt_referencia >= :dtInicio
          AND dt_referencia <= :dtFim
    """, nativeQuery = true)
    BigDecimal sumTotalDiariasByVendedorAndPeriodo(
            @Param("vendedorId") UUID vendedorId,
            @Param("dtInicio") LocalDate dtInicio,
            @Param("dtFim") LocalDate dtFim);

    /**
     * Sum total effective attendance values for all sellers in a period.
     * Used for dashboard/DRE calculations.
     *
     * @param tenantId Tenant UUID
     * @param dtInicio Start date (inclusive)
     * @param dtFim End date (inclusive)
     * @return Total attendance value for the period
     */
    @Query(value = """
        SELECT COALESCE(SUM(
            CASE WHEN valor_ajustado IS NOT NULL THEN valor_ajustado
                 ELSE valor_diaria END
        ), 0)
        FROM presenca_vendedor
        WHERE tenant_id = :tenantId
          AND dt_referencia >= :dtInicio
          AND dt_referencia <= :dtFim
    """, nativeQuery = true)
    BigDecimal sumTotalDiariasByTenantAndPeriodo(
            @Param("tenantId") UUID tenantId,
            @Param("dtInicio") LocalDate dtInicio,
            @Param("dtFim") LocalDate dtFim);

    // ========== Payment Tracking Queries ==========

    /**
     * Find unpaid attendance records for a seller.
     * Used when processing bulk payment.
     *
     * @param tenantId Tenant UUID
     * @param vendedorId Seller UUID
     * @return List of unpaid PresencaVendedor records
     */
    @Query("""
        SELECT p FROM PresencaVendedor p
        WHERE p.tenantId = :tenantId
          AND p.vendedorId = :vendedorId
          AND p.pagoEm IS NULL
        ORDER BY p.dtReferencia ASC
    """)
    List<PresencaVendedor> findNaoPagasByVendedor(
            @Param("tenantId") UUID tenantId,
            @Param("vendedorId") UUID vendedorId);

    /**
     * Sum total unpaid diarias for a seller.
     *
     * @param tenantId Tenant UUID
     * @param vendedorId Seller UUID
     * @return Total unpaid value
     */
    @Query(value = """
        SELECT COALESCE(SUM(
            CASE WHEN valor_ajustado IS NOT NULL THEN valor_ajustado
                 ELSE valor_diaria END
        ), 0)
        FROM presenca_vendedor
        WHERE tenant_id = :tenantId
          AND vendedor_id = :vendedorId
          AND pago_em IS NULL
    """, nativeQuery = true)
    BigDecimal sumDiariasNaoPagasByVendedor(
            @Param("tenantId") UUID tenantId,
            @Param("vendedorId") UUID vendedorId);

    /**
     * Count unpaid diarias for a seller.
     *
     * @param tenantId Tenant UUID
     * @param vendedorId Seller UUID
     * @return Count of unpaid diarias
     */
    @Query("""
        SELECT COUNT(p) FROM PresencaVendedor p
        WHERE p.tenantId = :tenantId
          AND p.vendedorId = :vendedorId
          AND p.pagoEm IS NULL
    """)
    int countDiariasNaoPagasByVendedor(
            @Param("tenantId") UUID tenantId,
            @Param("vendedorId") UUID vendedorId);
}
