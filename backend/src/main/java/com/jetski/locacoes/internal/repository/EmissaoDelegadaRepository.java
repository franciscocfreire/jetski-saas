package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.EmissaoDelegada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repository: espelho das emissões delegadas no tenant emissor.
 *
 * @author Jetski Team
 */
public interface EmissaoDelegadaRepository extends JpaRepository<EmissaoDelegada, UUID> {

    @Query("SELECT e FROM EmissaoDelegada e WHERE e.tenantId = :tenantId "
        + "AND (:operadoraId IS NULL OR e.operadoraTenantId = :operadoraId) "
        + "ORDER BY e.emitidoEm DESC")
    List<EmissaoDelegada> listar(@Param("tenantId") UUID tenantId,
                                 @Param("operadoraId") UUID operadoraId,
                                 org.springframework.data.domain.Pageable pageable);

    /** Contagem mensal por operadora — base do acerto financeiro por fora (§8.C). */
    @Query(value = "SELECT operadora_tenant_id, max(operadora_nome), "
        + "to_char(date_trunc('month', emitido_em AT TIME ZONE 'America/Sao_Paulo'), 'YYYY-MM'), count(*) "
        + "FROM emissao_delegada WHERE tenant_id = :tenantId "
        + "GROUP BY operadora_tenant_id, 3 ORDER BY 3 DESC, 2", nativeQuery = true)
    List<Object[]> contagensPorOperadoraMes(@Param("tenantId") UUID tenantId);

    java.util.Optional<EmissaoDelegada> findByIdAndTenantId(UUID id, UUID tenantId);
}
