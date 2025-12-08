package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.ModeloMidia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ModeloMidia entity.
 * Provides CRUD operations and custom queries for model media items.
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Repository
public interface ModeloMidiaRepository extends JpaRepository<ModeloMidia, UUID> {

    /**
     * Find all media for a model, ordered by ordem
     */
    List<ModeloMidia> findByModeloIdOrderByOrdemAsc(UUID modeloId);

    /**
     * Find all media for a model by type
     */
    List<ModeloMidia> findByModeloIdAndTipoOrderByOrdemAsc(UUID modeloId, ModeloMidia.TipoMidia tipo);

    /**
     * Find the principal (main) image for a model
     */
    Optional<ModeloMidia> findByModeloIdAndPrincipalTrue(UUID modeloId);

    /**
     * Count media items for a model
     */
    long countByModeloId(UUID modeloId);

    /**
     * Delete all media for a model
     */
    @Modifying
    void deleteByModeloId(UUID modeloId);

    /**
     * Clear principal flag for all media of a model (before setting a new principal)
     */
    @Modifying
    @Query("UPDATE ModeloMidia m SET m.principal = false WHERE m.modeloId = :modeloId")
    void clearPrincipalByModeloId(@Param("modeloId") UUID modeloId);

    /**
     * Find all media for multiple models (for marketplace batch loading)
     */
    @Query("SELECT m FROM ModeloMidia m WHERE m.modeloId IN :modeloIds ORDER BY m.modeloId, m.ordem")
    List<ModeloMidia> findByModeloIdIn(@Param("modeloIds") List<UUID> modeloIds);

    /**
     * Get next ordem value for a model
     */
    @Query("SELECT COALESCE(MAX(m.ordem), -1) + 1 FROM ModeloMidia m WHERE m.modeloId = :modeloId")
    Integer getNextOrdem(@Param("modeloId") UUID modeloId);
}
