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

    // ========== NOVOS MÉTODOS PARA VENDEDOR SERVICE ==========

    /**
     * Total de comissões pendentes por vendedor
     */
    @Query("SELECT COALESCE(SUM(c.valorComissao), 0) FROM Comissao c WHERE c.tenantId = :tenantId " +
           "AND c.vendedorId = :vendedorId AND c.status = 'PENDENTE'")
    java.math.BigDecimal sumComissoesPendentesByVendedor(@Param("tenantId") UUID tenantId,
                                                          @Param("vendedorId") UUID vendedorId);

    /**
     * Total de comissões aprovadas por vendedor
     */
    @Query("SELECT COALESCE(SUM(c.valorComissao), 0) FROM Comissao c WHERE c.tenantId = :tenantId " +
           "AND c.vendedorId = :vendedorId AND c.status = 'APROVADA'")
    java.math.BigDecimal sumComissoesAprovadasByVendedor(@Param("tenantId") UUID tenantId,
                                                          @Param("vendedorId") UUID vendedorId);

    /**
     * Conta comissões aprovadas por vendedor (para pagamento)
     */
    @Query("SELECT COUNT(c) FROM Comissao c WHERE c.tenantId = :tenantId " +
           "AND c.vendedorId = :vendedorId AND c.status = 'APROVADA'")
    int countComissoesAprovadasByVendedor(@Param("tenantId") UUID tenantId,
                                          @Param("vendedorId") UUID vendedorId);

    /**
     * Total de comissões pagas por vendedor (all time)
     */
    @Query("SELECT COALESCE(SUM(c.valorComissao), 0) FROM Comissao c WHERE c.tenantId = :tenantId " +
           "AND c.vendedorId = :vendedorId AND c.status = 'PAGA'")
    java.math.BigDecimal sumComissoesPagasAllTimeByVendedor(@Param("tenantId") UUID tenantId,
                                                             @Param("vendedorId") UUID vendedorId);

    /**
     * Conta total de locações por vendedor
     */
    @Query("SELECT COUNT(c) FROM Comissao c WHERE c.tenantId = :tenantId AND c.vendedorId = :vendedorId")
    Long countLocacoesByVendedor(@Param("tenantId") UUID tenantId, @Param("vendedorId") UUID vendedorId);

    /**
     * Conta vendas acima do preço base por vendedor (para bonus)
     */
    @Query("SELECT COUNT(c) FROM Comissao c WHERE c.tenantId = :tenantId AND c.vendedorId = :vendedorId " +
           "AND c.vendaAcimaPrecoBase = true")
    Long countVendasAcimaPrecoBaseByVendedor(@Param("tenantId") UUID tenantId, @Param("vendedorId") UUID vendedorId);

    /**
     * Busca comissões aprovadas por vendedor (para pagamento em lote)
     */
    @Query("SELECT c FROM Comissao c WHERE c.tenantId = :tenantId AND c.vendedorId = :vendedorId " +
           "AND c.status = 'APROVADA' ORDER BY c.dataLocacao ASC")
    List<Comissao> findAprovadasByVendedor(@Param("tenantId") UUID tenantId, @Param("vendedorId") UUID vendedorId);

    /**
     * Busca comissões por vendedor ordenadas por data de criação
     */
    List<Comissao> findByTenantIdAndVendedorIdOrderByCreatedAtDesc(UUID tenantId, UUID vendedorId);

    /**
     * Busca comissões por vendedor e status ordenadas por data de criação
     */
    List<Comissao> findByTenantIdAndVendedorIdAndStatusOrderByCreatedAtDesc(UUID tenantId, UUID vendedorId, StatusComissao status);
}
