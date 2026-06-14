package com.jetski.manutencao.internal.repository;

import com.jetski.despesas.domain.StatusDespesa;
import com.jetski.manutencao.domain.DespesaManutencao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for DespesaManutencao (Maintenance Expenses)
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Repository
public interface DespesaManutencaoRepository extends JpaRepository<DespesaManutencao, UUID> {

    // ========== Listagens basicas ==========

    /**
     * Busca despesas por periodo de vencimento
     */
    List<DespesaManutencao> findByTenantIdAndDtVencimentoBetweenOrderByDtVencimentoAsc(
            UUID tenantId, LocalDate dataInicio, LocalDate dataFim);

    /**
     * Busca despesas por OS de manutencao
     */
    List<DespesaManutencao> findByTenantIdAndOsManutencaoIdOrderByNumeroParcelaAsc(
            UUID tenantId, UUID osManutencaoId);

    /**
     * Busca despesas por status
     */
    List<DespesaManutencao> findByTenantIdAndStatusOrderByDtVencimentoAsc(
            UUID tenantId, StatusDespesa status);

    /**
     * Busca despesas por dia especifico
     */
    List<DespesaManutencao> findByTenantIdAndDtVencimentoOrderByCreatedAtDesc(
            UUID tenantId, LocalDate dtVencimento);

    // ========== Queries para workflow ==========

    /**
     * Busca despesas pendentes de aprovacao
     */
    @Query("SELECT d FROM DespesaManutencao d WHERE d.tenantId = :tenantId " +
           "AND d.status = 'PENDENTE' ORDER BY d.dtVencimento ASC, d.numeroParcela ASC")
    List<DespesaManutencao> findPendentesAprovacao(@Param("tenantId") UUID tenantId);

    /**
     * Busca despesas aprovadas aguardando pagamento
     */
    @Query("SELECT d FROM DespesaManutencao d WHERE d.tenantId = :tenantId " +
           "AND d.status = 'APROVADA' ORDER BY d.dtVencimento ASC, d.numeroParcela ASC")
    List<DespesaManutencao> findAguardandoPagamento(@Param("tenantId") UUID tenantId);

    // ========== Queries para consolidacao/fechamento ==========

    /**
     * Soma total de despesas por dia (status APROVADA ou PAGA)
     */
    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM DespesaManutencao d " +
           "WHERE d.tenantId = :tenantId AND d.dtVencimento = :data " +
           "AND d.status IN ('APROVADA', 'PAGA')")
    BigDecimal sumByTenantIdAndDtVencimento(
            @Param("tenantId") UUID tenantId,
            @Param("data") LocalDate data);

    /**
     * Soma total de despesas por periodo
     */
    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM DespesaManutencao d " +
           "WHERE d.tenantId = :tenantId " +
           "AND d.dtVencimento BETWEEN :dataInicio AND :dataFim " +
           "AND d.status IN ('APROVADA', 'PAGA')")
    BigDecimal sumByTenantIdAndPeriodo(
            @Param("tenantId") UUID tenantId,
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim);

    // ========== Queries para dashboard ==========

    /**
     * Conta despesas pendentes
     */
    @Query("SELECT COUNT(d) FROM DespesaManutencao d WHERE d.tenantId = :tenantId " +
           "AND d.status = 'PENDENTE'")
    long countPendentes(@Param("tenantId") UUID tenantId);

    /**
     * Soma valor das despesas pendentes
     */
    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM DespesaManutencao d " +
           "WHERE d.tenantId = :tenantId AND d.status = 'PENDENTE'")
    BigDecimal sumValorPendentes(@Param("tenantId") UUID tenantId);

    /**
     * Conta despesas aprovadas aguardando pagamento
     */
    @Query("SELECT COUNT(d) FROM DespesaManutencao d WHERE d.tenantId = :tenantId " +
           "AND d.status = 'APROVADA'")
    long countAguardandoPagamento(@Param("tenantId") UUID tenantId);

    /**
     * Soma valor das despesas aguardando pagamento
     */
    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM DespesaManutencao d " +
           "WHERE d.tenantId = :tenantId AND d.status = 'APROVADA'")
    BigDecimal sumValorAguardandoPagamento(@Param("tenantId") UUID tenantId);

    /**
     * Verifica se ja existem despesas geradas para uma OS
     */
    @Query("SELECT COUNT(d) > 0 FROM DespesaManutencao d " +
           "WHERE d.tenantId = :tenantId AND d.osManutencaoId = :osManutencaoId " +
           "AND d.status NOT IN ('CANCELADA', 'REJEITADA')")
    boolean existsDespesaAtivaByOsManutencaoId(
            @Param("tenantId") UUID tenantId,
            @Param("osManutencaoId") UUID osManutencaoId);

    /**
     * Soma total por OS (todas as parcelas ativas)
     */
    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM DespesaManutencao d " +
           "WHERE d.tenantId = :tenantId AND d.osManutencaoId = :osManutencaoId " +
           "AND d.status NOT IN ('CANCELADA', 'REJEITADA')")
    BigDecimal sumValorByOsManutencaoId(
            @Param("tenantId") UUID tenantId,
            @Param("osManutencaoId") UUID osManutencaoId);
}
