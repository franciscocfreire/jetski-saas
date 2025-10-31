package com.jetski.fechamento.internal.repository;

import com.jetski.fechamento.domain.FechamentoDiario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for FechamentoDiario (Daily Closure)
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Repository
public interface FechamentoDiarioRepository extends JpaRepository<FechamentoDiario, UUID> {

    /**
     * Busca fechamento diário por tenant e data de referência
     */
    Optional<FechamentoDiario> findByTenantIdAndDtReferencia(UUID tenantId, LocalDate dtReferencia);

    /**
     * Busca fechamentos diários por tenant e intervalo de datas
     */
    List<FechamentoDiario> findByTenantIdAndDtReferenciaBetweenOrderByDtReferenciaDesc(
            UUID tenantId,
            LocalDate dataInicio,
            LocalDate dataFim
    );

    /**
     * Busca fechamentos diários bloqueados
     */
    List<FechamentoDiario> findByTenantIdAndBloqueadoTrueOrderByDtReferenciaDesc(UUID tenantId);

    /**
     * Busca fechamentos diários por status
     */
    List<FechamentoDiario> findByTenantIdAndStatusOrderByDtReferenciaDesc(UUID tenantId, String status);

    /**
     * Busca fechamentos diários em aberto (não fechados)
     */
    @Query("SELECT f FROM FechamentoDiario f WHERE f.tenantId = :tenantId AND f.status = 'aberto' ORDER BY f.dtReferencia DESC")
    List<FechamentoDiario> findAbertos(@Param("tenantId") UUID tenantId);

    /**
     * Verifica se existe fechamento bloqueado para uma data específica
     */
    @Query("SELECT COUNT(f) > 0 FROM FechamentoDiario f WHERE f.tenantId = :tenantId AND f.dtReferencia = :data AND f.bloqueado = true")
    boolean existsBloqueadoParaData(@Param("tenantId") UUID tenantId, @Param("data") LocalDate data);

    /**
     * Verifica se existe fechamento bloqueado para um intervalo de datas
     */
    @Query("SELECT COUNT(f) > 0 FROM FechamentoDiario f WHERE f.tenantId = :tenantId AND f.dtReferencia BETWEEN :dataInicio AND :dataFim AND f.bloqueado = true")
    boolean existsBloqueadoNoIntervalo(
            @Param("tenantId") UUID tenantId,
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim
    );
}
