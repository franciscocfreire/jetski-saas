package com.jetski.pagamentos.internal.repository;

import com.jetski.pagamentos.domain.PagamentoVendedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository: PagamentoVendedorRepository
 *
 * Handles database operations for seller payments (PagamentoVendedor).
 * RLS (Row Level Security) automatically filters queries by tenant_id.
 *
 * @author Jetski Team
 * @since 0.12.0
 */
@Repository
public interface PagamentoVendedorRepository extends JpaRepository<PagamentoVendedor, UUID> {

    /**
     * Find all payments for a tenant, ordered by creation date.
     *
     * @param tenantId Tenant UUID
     * @return List of payments
     */
    List<PagamentoVendedor> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /**
     * Find all payments for a specific seller, ordered by creation date.
     *
     * @param tenantId Tenant UUID
     * @param vendedorId Seller UUID
     * @return List of payments for the seller
     */
    List<PagamentoVendedor> findByTenantIdAndVendedorIdOrderByCreatedAtDesc(
            UUID tenantId, UUID vendedorId);

    /**
     * Find payments within a date range.
     *
     * @param tenantId Tenant UUID
     * @param dtInicio Start date
     * @param dtFim End date
     * @return List of payments in the period
     */
    @Query("""
        SELECT p FROM PagamentoVendedor p
        WHERE p.tenantId = :tenantId
          AND p.createdAt >= :dtInicio
          AND p.createdAt <= :dtFim
        ORDER BY p.createdAt DESC
    """)
    List<PagamentoVendedor> findByTenantIdAndPeriodo(
            @Param("tenantId") UUID tenantId,
            @Param("dtInicio") java.time.Instant dtInicio,
            @Param("dtFim") java.time.Instant dtFim);

    /**
     * Sum total payments for a seller.
     *
     * @param tenantId Tenant UUID
     * @param vendedorId Seller UUID
     * @return Total amount paid
     */
    @Query("""
        SELECT COALESCE(SUM(p.valorTotal), 0)
        FROM PagamentoVendedor p
        WHERE p.tenantId = :tenantId
          AND p.vendedorId = :vendedorId
    """)
    BigDecimal sumTotalPagoByVendedor(
            @Param("tenantId") UUID tenantId,
            @Param("vendedorId") UUID vendedorId);

    /**
     * Sum total payments for all sellers in a period.
     *
     * @param tenantId Tenant UUID
     * @param dtInicio Start date
     * @param dtFim End date
     * @return Total amount paid in the period
     */
    @Query(value = """
        SELECT COALESCE(SUM(valor_total), 0)
        FROM pagamento_vendedor
        WHERE tenant_id = :tenantId
          AND created_at >= :dtInicio
          AND created_at <= :dtFim
    """, nativeQuery = true)
    BigDecimal sumTotalPagoByPeriodo(
            @Param("tenantId") UUID tenantId,
            @Param("dtInicio") java.time.Instant dtInicio,
            @Param("dtFim") java.time.Instant dtFim);

    /**
     * Count payments for a seller.
     *
     * @param tenantId Tenant UUID
     * @param vendedorId Seller UUID
     * @return Count of payments
     */
    @Query("""
        SELECT COUNT(p) FROM PagamentoVendedor p
        WHERE p.tenantId = :tenantId
          AND p.vendedorId = :vendedorId
    """)
    long countByVendedor(
            @Param("tenantId") UUID tenantId,
            @Param("vendedorId") UUID vendedorId);
}
