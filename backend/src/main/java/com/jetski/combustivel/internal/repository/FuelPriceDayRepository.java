package com.jetski.combustivel.internal.repository;

import com.jetski.combustivel.domain.FuelPriceDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FuelPriceDayRepository extends JpaRepository<FuelPriceDay, Long> {

    Optional<FuelPriceDay> findByTenantIdAndData(UUID tenantId, LocalDate data);

    @Query("SELECT f FROM FuelPriceDay f WHERE f.tenantId = :tenantId " +
           "AND f.data >= :dataInicio AND f.data <= :dataFim " +
           "ORDER BY f.data DESC")
    List<FuelPriceDay> findByTenantIdAndDataBetween(
        @Param("tenantId") UUID tenantId,
        @Param("dataInicio") LocalDate dataInicio,
        @Param("dataFim") LocalDate dataFim
    );

    @Query("SELECT AVG(f.precoMedioLitro) FROM FuelPriceDay f " +
           "WHERE f.tenantId = :tenantId " +
           "AND f.data >= :dataInicio AND f.data <= :dataFim")
    Optional<java.math.BigDecimal> findAveragePrice(
        @Param("tenantId") UUID tenantId,
        @Param("dataInicio") LocalDate dataInicio,
        @Param("dataFim") LocalDate dataFim
    );
}
