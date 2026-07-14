package com.jetski.creditos.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CreditoLancamentoRepository extends JpaRepository<CreditoLancamento, UUID> {

    @Query("SELECT COALESCE(SUM(c.quantidade), 0) FROM CreditoLancamento c WHERE c.tenantId = :tenantId")
    int saldo(@Param("tenantId") UUID tenantId);

    boolean existsByTenantIdAndTipo(UUID tenantId, TipoLancamento tipo);

    boolean existsByTenantIdAndReferenciaId(UUID tenantId, UUID referenciaId);

    /**
     * Bônus de adesão ainda não estornado: soma dos grants ADESAO (positivos)
     * com os estornos de bônus já aplicados (negativos, identificados pelo
     * prefixo do motivo). Consumo normal não entra — ele queima o saldo, e o
     * estorno é limitado ao saldo pelo chamador.
     */
    @Query("SELECT COALESCE(SUM(c.quantidade), 0) FROM CreditoLancamento c "
        + "WHERE c.tenantId = :tenantId AND (c.tipo = :adesao "
        + "OR (c.tipo = :estorno AND c.motivo LIKE :motivoBonus))")
    int bonusRestante(@Param("tenantId") UUID tenantId,
                      @Param("adesao") TipoLancamento adesao,
                      @Param("estorno") TipoLancamento estorno,
                      @Param("motivoBonus") String motivoBonus);

    List<CreditoLancamento> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, org.springframework.data.domain.Pageable pageable);
}
