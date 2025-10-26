package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.Locacao;
import com.jetski.locacoes.domain.LocacaoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository: LocacaoRepository
 *
 * Data access layer for Locacao (Rental) entity.
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Repository
public interface LocacaoRepository extends JpaRepository<Locacao, UUID> {

    /**
     * Find locacao by ID and tenant
     */
    Optional<Locacao> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Find all locacoes for a tenant
     */
    List<Locacao> findByTenantId(UUID tenantId);

    /**
     * Find all locacoes by status for a tenant
     */
    List<Locacao> findByTenantIdAndStatusOrderByDataCheckInDesc(UUID tenantId, LocacaoStatus status);

    /**
     * Find all locacoes for a specific jetski
     */
    List<Locacao> findByTenantIdAndJetskiIdOrderByDataCheckInDesc(UUID tenantId, UUID jetskiId);

    /**
     * Find all locacoes for a specific cliente
     */
    List<Locacao> findByTenantIdAndClienteIdOrderByDataCheckInDesc(UUID tenantId, UUID clienteId);

    /**
     * Find all locacoes in a date range
     */
    @Query("SELECT l FROM Locacao l WHERE l.tenantId = :tenantId " +
           "AND l.dataCheckIn >= :dataInicio " +
           "AND l.dataCheckIn <= :dataFim " +
           "ORDER BY l.dataCheckIn DESC")
    List<Locacao> findByTenantIdAndDateRange(
        @Param("tenantId") UUID tenantId,
        @Param("dataInicio") LocalDateTime dataInicio,
        @Param("dataFim") LocalDateTime dataFim
    );

    /**
     * Find active rental for a specific jetski (status = EM_CURSO)
     * Used to prevent double check-in
     */
    Optional<Locacao> findByTenantIdAndJetskiIdAndStatus(UUID tenantId, UUID jetskiId, LocacaoStatus status);

    /**
     * Find locacao created from a reservation
     */
    Optional<Locacao> findByTenantIdAndReservaId(UUID tenantId, UUID reservaId);

    /**
     * Count active rentals (EM_CURSO) for tenant
     */
    long countByTenantIdAndStatus(UUID tenantId, LocacaoStatus status);

    /**
     * Check if jetski has active rental
     */
    boolean existsByTenantIdAndJetskiIdAndStatus(UUID tenantId, UUID jetskiId, LocacaoStatus status);
}
