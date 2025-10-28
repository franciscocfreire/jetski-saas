package com.jetski.combustivel.internal.repository;

import com.jetski.combustivel.domain.Abastecimento;
import com.jetski.combustivel.domain.TipoAbastecimento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AbastecimentoRepository extends JpaRepository<Abastecimento, Long> {

    List<Abastecimento> findByTenantIdAndLocacaoIdOrderByDataHoraAsc(UUID tenantId, UUID locacaoId);

    List<Abastecimento> findByTenantIdAndJetskiIdOrderByDataHoraDesc(UUID tenantId, UUID jetskiId);

    @Query("SELECT a FROM Abastecimento a WHERE a.tenantId = :tenantId " +
           "AND (:jetskiId IS NULL OR a.jetskiId = :jetskiId) " +
           "AND (:locacaoId IS NULL OR a.locacaoId = :locacaoId) " +
           "AND (:dataInicio IS NULL OR a.dataHora >= :dataInicio) " +
           "AND (:dataFim IS NULL OR a.dataHora <= :dataFim) " +
           "ORDER BY a.dataHora DESC")
    Page<Abastecimento> findWithFilters(
        @Param("tenantId") UUID tenantId,
        @Param("jetskiId") UUID jetskiId,
        @Param("locacaoId") UUID locacaoId,
        @Param("dataInicio") Instant dataInicio,
        @Param("dataFim") Instant dataFim,
        Pageable pageable
    );

    @Query("SELECT a FROM Abastecimento a WHERE a.tenantId = :tenantId " +
           "AND DATE(a.dataHora) = :data " +
           "ORDER BY a.dataHora ASC")
    List<Abastecimento> findByTenantIdAndData(@Param("tenantId") UUID tenantId, @Param("data") LocalDate data);
}
