package com.jetski.bonus.internal.repository;

import com.jetski.bonus.domain.BonusVendedor;
import com.jetski.bonus.domain.StatusBonus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for BonusVendedor
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Repository
public interface BonusVendedorRepository extends JpaRepository<BonusVendedor, UUID> {

    /**
     * Find all bonuses for a seller
     */
    List<BonusVendedor> findByTenantIdAndVendedorIdOrderByMetaAtingidaDesc(UUID tenantId, UUID vendedorId);

    /**
     * Find bonuses by status
     */
    List<BonusVendedor> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, StatusBonus status);

    /**
     * Find the highest milestone achieved by a seller (to avoid duplicates)
     */
    @Query("SELECT MAX(b.metaAtingida) FROM BonusVendedor b WHERE b.tenantId = :tenantId AND b.vendedorId = :vendedorId")
    Optional<Integer> findUltimaMetaAtingida(@Param("tenantId") UUID tenantId, @Param("vendedorId") UUID vendedorId);

    /**
     * Check if a specific milestone bonus already exists for a seller
     */
    boolean existsByTenantIdAndVendedorIdAndMetaAtingida(UUID tenantId, UUID vendedorId, Integer metaAtingida);

    /**
     * Find pending bonuses for a seller
     */
    List<BonusVendedor> findByTenantIdAndVendedorIdAndStatus(UUID tenantId, UUID vendedorId, StatusBonus status);

    /**
     * Count pending bonuses for tenant
     */
    Long countByTenantIdAndStatus(UUID tenantId, StatusBonus status);

    // ========== PAYMENT QUERIES ==========

    /**
     * Sum approved bonuses for a seller (for payment pending calculation)
     */
    @Query("SELECT COALESCE(SUM(b.valorBonus), 0) FROM BonusVendedor b WHERE b.tenantId = :tenantId " +
           "AND b.vendedorId = :vendedorId AND b.status = 'APROVADO'")
    java.math.BigDecimal sumBonusAprovadosByVendedor(@Param("tenantId") UUID tenantId,
                                                      @Param("vendedorId") UUID vendedorId);

    /**
     * Count approved bonuses for a seller (for payment)
     */
    @Query("SELECT COUNT(b) FROM BonusVendedor b WHERE b.tenantId = :tenantId " +
           "AND b.vendedorId = :vendedorId AND b.status = 'APROVADO'")
    int countBonusAprovadosByVendedor(@Param("tenantId") UUID tenantId,
                                       @Param("vendedorId") UUID vendedorId);

    /**
     * Find approved bonuses for a seller (for bulk payment)
     */
    @Query("SELECT b FROM BonusVendedor b WHERE b.tenantId = :tenantId AND b.vendedorId = :vendedorId " +
           "AND b.status = 'APROVADO' ORDER BY b.createdAt ASC")
    List<BonusVendedor> findAprovadosByVendedor(@Param("tenantId") UUID tenantId,
                                                 @Param("vendedorId") UUID vendedorId);
}
