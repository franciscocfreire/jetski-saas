package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.Foto;
import com.jetski.locacoes.domain.FotoTipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository: FotoRepository
 *
 * Data access layer for Foto (Photo) entity.
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Repository
public interface FotoRepository extends JpaRepository<Foto, UUID> {

    /**
     * Find foto by ID and tenant
     */
    Optional<Foto> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Find all fotos for a locacao
     */
    List<Foto> findByTenantIdAndLocacaoIdOrderByCreatedAtAsc(UUID tenantId, UUID locacaoId);

    /**
     * Find fotos by tipo for a locacao
     */
    List<Foto> findByTenantIdAndLocacaoIdAndTipoOrderByCreatedAtAsc(
        UUID tenantId,
        UUID locacaoId,
        FotoTipo tipo
    );

    /**
     * Find all fotos for a jetski (maintenance, etc.)
     */
    List<Foto> findByTenantIdAndJetskiIdOrderByCreatedAtDesc(UUID tenantId, UUID jetskiId);

    /**
     * Count fotos by tipo for a locacao
     */
    long countByTenantIdAndLocacaoIdAndTipo(UUID tenantId, UUID locacaoId, FotoTipo tipo);

    /**
     * Check if locacao has check-in photos
     */
    boolean existsByTenantIdAndLocacaoIdAndTipo(UUID tenantId, UUID locacaoId, FotoTipo tipo);

    /**
     * Find foto by tenant, locacao and tipo (for orphan cleanup)
     */
    Optional<Foto> findByTenantIdAndLocacaoIdAndTipo(UUID tenantId, UUID locacaoId, FotoTipo tipo);

    /**
     * Find foto by S3 key (for integrity checks)
     */
    Optional<Foto> findByTenantIdAndS3Key(UUID tenantId, String s3Key);
}
