package com.jetski.comissoes.internal.repository;

import com.jetski.comissoes.domain.Comissao;
import com.jetski.comissoes.domain.StatusComissao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Comissao (Commissions)
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Repository
public interface ComissaoRepository extends JpaRepository<Comissao, UUID> {

    /**
     * Busca comissão por locação
     */
    Optional<Comissao> findByTenantIdAndLocacaoId(UUID tenantId, UUID locacaoId);

    /**
     * Busca comissões por vendedor
     */
    List<Comissao> findByTenantIdAndVendedorIdOrderByDataLocacaoDesc(UUID tenantId, UUID vendedorId);

    /**
     * Busca comissões por status
     */
    List<Comissao> findByTenantIdAndStatusOrderByDataLocacaoDesc(UUID tenantId, StatusComissao status);

    /**
     * Busca comissões pendentes de aprovação
     */
    @Query("SELECT c FROM Comissao c WHERE c.tenantId = :tenantId AND c.status = 'PENDENTE' " +
           "ORDER BY c.dataLocacao DESC")
    List<Comissao> findPendentesAprovacao(@Param("tenantId") UUID tenantId);

    /**
     * Busca comissões aprovadas aguardando pagamento
     */
    @Query("SELECT c FROM Comissao c WHERE c.tenantId = :tenantId AND c.status = 'APROVADA' " +
           "ORDER BY c.dataLocacao DESC")
    List<Comissao> findAguardandoPagamento(@Param("tenantId") UUID tenantId);

    /**
     * Busca comissões por período (para fechamento mensal)
     */
    @Query("SELECT c FROM Comissao c WHERE c.tenantId = :tenantId " +
           "AND c.dataLocacao BETWEEN :inicio AND :fim " +
           "ORDER BY c.dataLocacao ASC")
    List<Comissao> findByPeriodo(@Param("tenantId") UUID tenantId,
                                  @Param("inicio") Instant inicio,
                                  @Param("fim") Instant fim);

    /**
     * Total de comissões pagas por vendedor em um período
     */
    @Query("SELECT SUM(c.valorComissao) FROM Comissao c WHERE c.tenantId = :tenantId " +
           "AND c.vendedorId = :vendedorId AND c.status = 'PAGA' " +
           "AND c.dataLocacao BETWEEN :inicio AND :fim")
    Double sumComissoesPagasByVendedor(@Param("tenantId") UUID tenantId,
                                       @Param("vendedorId") UUID vendedorId,
                                       @Param("inicio") Instant inicio,
                                       @Param("fim") Instant fim);
}
