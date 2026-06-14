package com.jetski.despesas.internal.repository;

import com.jetski.despesas.domain.CategoriaDespesa;
import com.jetski.despesas.domain.DespesaOperacional;
import com.jetski.despesas.domain.StatusDespesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for DespesaOperacional (Operational Expenses)
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Repository
public interface DespesaOperacionalRepository extends JpaRepository<DespesaOperacional, UUID> {

    // ========== Listagens basicas ==========

    /**
     * Busca despesas por periodo
     */
    List<DespesaOperacional> findByTenantIdAndDtReferenciaBetweenOrderByDtReferenciaDesc(
            UUID tenantId, LocalDate dataInicio, LocalDate dataFim);

    /**
     * Busca despesas por dia especifico
     */
    List<DespesaOperacional> findByTenantIdAndDtReferenciaOrderByCreatedAtDesc(
            UUID tenantId, LocalDate dtReferencia);

    /**
     * Busca despesas por categoria
     */
    List<DespesaOperacional> findByTenantIdAndCategoriaOrderByDtReferenciaDesc(
            UUID tenantId, CategoriaDespesa categoria);

    /**
     * Busca despesas por status
     */
    List<DespesaOperacional> findByTenantIdAndStatusOrderByDtReferenciaDesc(
            UUID tenantId, StatusDespesa status);

    /**
     * Busca despesas por responsavel
     */
    List<DespesaOperacional> findByTenantIdAndResponsavelIdOrderByDtReferenciaDesc(
            UUID tenantId, UUID responsavelId);

    // ========== Queries para workflow ==========

    /**
     * Busca despesas pendentes de aprovacao
     */
    @Query("SELECT d FROM DespesaOperacional d WHERE d.tenantId = :tenantId " +
           "AND d.status = 'PENDENTE' ORDER BY d.dtReferencia DESC, d.createdAt DESC")
    List<DespesaOperacional> findPendentesAprovacao(@Param("tenantId") UUID tenantId);

    /**
     * Busca despesas aprovadas aguardando pagamento
     */
    @Query("SELECT d FROM DespesaOperacional d WHERE d.tenantId = :tenantId " +
           "AND d.status = 'APROVADA' ORDER BY d.dtReferencia ASC, d.createdAt ASC")
    List<DespesaOperacional> findAguardandoPagamento(@Param("tenantId") UUID tenantId);

    // ========== Queries para consolidacao/fechamento ==========

    /**
     * Soma total de despesas por dia (todas as categorias, status APROVADA ou PAGA)
     */
    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM DespesaOperacional d " +
           "WHERE d.tenantId = :tenantId AND d.dtReferencia = :data " +
           "AND d.status IN ('APROVADA', 'PAGA')")
    BigDecimal sumByTenantIdAndDtReferencia(
            @Param("tenantId") UUID tenantId,
            @Param("data") LocalDate data);

    /**
     * Soma total de despesas por periodo
     */
    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM DespesaOperacional d " +
           "WHERE d.tenantId = :tenantId " +
           "AND d.dtReferencia BETWEEN :dataInicio AND :dataFim " +
           "AND d.status IN ('APROVADA', 'PAGA')")
    BigDecimal sumByTenantIdAndPeriodo(
            @Param("tenantId") UUID tenantId,
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim);

    /**
     * Soma despesas por categoria em um periodo
     */
    @Query("SELECT d.categoria, COALESCE(SUM(d.valor), 0) FROM DespesaOperacional d " +
           "WHERE d.tenantId = :tenantId " +
           "AND d.dtReferencia BETWEEN :dataInicio AND :dataFim " +
           "AND d.status IN ('APROVADA', 'PAGA') " +
           "GROUP BY d.categoria")
    List<Object[]> sumByCategoriaPeriodo(
            @Param("tenantId") UUID tenantId,
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim);

    // ========== Queries para dashboard ==========

    /**
     * Conta despesas pendentes
     */
    @Query("SELECT COUNT(d) FROM DespesaOperacional d WHERE d.tenantId = :tenantId " +
           "AND d.status = 'PENDENTE'")
    long countPendentes(@Param("tenantId") UUID tenantId);

    /**
     * Soma valor das despesas pendentes
     */
    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM DespesaOperacional d " +
           "WHERE d.tenantId = :tenantId AND d.status = 'PENDENTE'")
    BigDecimal sumValorPendentes(@Param("tenantId") UUID tenantId);

    /**
     * Conta despesas aprovadas aguardando pagamento
     */
    @Query("SELECT COUNT(d) FROM DespesaOperacional d WHERE d.tenantId = :tenantId " +
           "AND d.status = 'APROVADA'")
    long countAguardandoPagamento(@Param("tenantId") UUID tenantId);

    /**
     * Soma valor das despesas aguardando pagamento
     */
    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM DespesaOperacional d " +
           "WHERE d.tenantId = :tenantId AND d.status = 'APROVADA'")
    BigDecimal sumValorAguardandoPagamento(@Param("tenantId") UUID tenantId);
}
