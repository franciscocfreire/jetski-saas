package com.jetski.fechamento.internal.repository;

import com.jetski.fechamento.domain.FechamentoMensal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for FechamentoMensal (Monthly Closure)
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Repository
public interface FechamentoMensalRepository extends JpaRepository<FechamentoMensal, UUID> {

    /**
     * Busca fechamento mensal por tenant, ano e mês
     */
    Optional<FechamentoMensal> findByTenantIdAndAnoAndMes(UUID tenantId, Integer ano, Integer mes);

    /**
     * Busca fechamentos mensais bloqueados
     */
    List<FechamentoMensal> findByTenantIdAndBloqueadoTrueOrderByAnoDescMesDesc(UUID tenantId);

    /**
     * Busca fechamentos mensais por status
     */
    List<FechamentoMensal> findByTenantIdAndStatusOrderByAnoDescMesDesc(UUID tenantId, String status);

    /**
     * Busca fechamentos mensais por ano
     */
    List<FechamentoMensal> findByTenantIdAndAnoOrderByMesAsc(UUID tenantId, Integer ano);

    /**
     * Busca fechamentos mensais em aberto (não fechados)
     */
    @Query("SELECT f FROM FechamentoMensal f WHERE f.tenantId = :tenantId AND f.status = 'aberto' ORDER BY f.ano DESC, f.mes DESC")
    List<FechamentoMensal> findAbertos(@Param("tenantId") UUID tenantId);

    /**
     * Verifica se existe fechamento bloqueado para um mês específico
     */
    @Query("SELECT COUNT(f) > 0 FROM FechamentoMensal f WHERE f.tenantId = :tenantId AND f.ano = :ano AND f.mes = :mes AND f.bloqueado = true")
    boolean existsBloqueadoParaMes(
            @Param("tenantId") UUID tenantId,
            @Param("ano") Integer ano,
            @Param("mes") Integer mes
    );

    /**
     * Lista todos os fechamentos mensais ordenados por período
     */
    List<FechamentoMensal> findByTenantIdOrderByAnoDescMesDesc(UUID tenantId);
}
