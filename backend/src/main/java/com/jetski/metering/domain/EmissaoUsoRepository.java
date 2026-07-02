package com.jetski.metering.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EmissaoUsoRepository extends JpaRepository<EmissaoUso, UUID> {

    boolean existsByTipoAndReferenciaIdAndOcorridoEm(TipoEmissao tipo, UUID referenciaId, Instant ocorridoEm);

    /**
     * Série mensal por tipo do tenant. RLS já filtra pelo tenant da sessão;
     * o filtro explícito é defesa em profundidade.
     */
    @Query(value = """
        SELECT to_char(date_trunc('month', e.ocorrido_em), 'YYYY-MM') AS competencia,
               e.tipo AS tipo,
               count(*) AS total
        FROM emissao_uso e
        WHERE e.tenant_id = :tenantId AND e.ocorrido_em >= :inicio
        GROUP BY 1, 2
        ORDER BY 1
        """, nativeQuery = true)
    List<Object[]> contarPorMesETipo(@Param("tenantId") UUID tenantId, @Param("inicio") Instant inicio);

    /** Totais por tipo num intervalo (usado pela visão da plataforma, tenant a tenant). */
    @Query(value = """
        SELECT e.tipo AS tipo, count(*) AS total
        FROM emissao_uso e
        WHERE e.tenant_id = :tenantId AND e.ocorrido_em >= :inicio AND e.ocorrido_em < :fim
        GROUP BY 1
        """, nativeQuery = true)
    List<Object[]> contarPorTipoNoPeriodo(@Param("tenantId") UUID tenantId,
                                          @Param("inicio") Instant inicio,
                                          @Param("fim") Instant fim);
}
