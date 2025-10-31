package com.jetski.comissoes.internal.repository;

import com.jetski.comissoes.domain.NivelPolitica;
import com.jetski.comissoes.domain.PoliticaComissao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for PoliticaComissao (Commission Policies)
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Repository
public interface PoliticaComissaoRepository extends JpaRepository<PoliticaComissao, UUID> {

    /**
     * Busca políticas ativas de um tenant ordenadas por prioridade (nível hierárquico)
     */
    List<PoliticaComissao> findByTenantIdAndAtivaOrderByNivelAsc(UUID tenantId, Boolean ativa);

    /**
     * Busca política de CAMPANHA ativa e vigente para um código específico
     */
    @Query("SELECT p FROM PoliticaComissao p WHERE p.tenantId = :tenantId " +
           "AND p.nivel = 'CAMPANHA' AND p.codigoCampanha = :codigoCampanha " +
           "AND p.ativa = true " +
           "AND (p.vigenciaInicio IS NULL OR p.vigenciaInicio <= :agora) " +
           "AND (p.vigenciaFim IS NULL OR p.vigenciaFim >= :agora)")
    List<PoliticaComissao> findCampanhaAtiva(@Param("tenantId") UUID tenantId,
                                              @Param("codigoCampanha") String codigoCampanha,
                                              @Param("agora") Instant agora);

    /**
     * Busca política de MODELO ativa
     */
    List<PoliticaComissao> findByTenantIdAndNivelAndModeloIdAndAtiva(
            UUID tenantId, NivelPolitica nivel, UUID modeloId, Boolean ativa);

    /**
     * Busca política de VENDEDOR ativa
     */
    List<PoliticaComissao> findByTenantIdAndNivelAndVendedorIdAndAtiva(
            UUID tenantId, NivelPolitica nivel, UUID vendedorId, Boolean ativa);

    /**
     * Busca políticas de DURACAO ativas
     */
    List<PoliticaComissao> findByTenantIdAndNivelAndAtivaOrderByDuracaoMinMinutosDesc(
            UUID tenantId, NivelPolitica nivel, Boolean ativa);
}
