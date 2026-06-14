package com.jetski.locacoes.api.dto;

import com.jetski.bonus.domain.StatusBonus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for BonusVendedor
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BonusVendedorResponse {

    private UUID id;
    private UUID vendedorId;
    private Integer metaAtingida;
    private BigDecimal valorBonus;
    private StatusBonus status;

    // Aprovação
    private UUID aprovadoPor;
    private Instant aprovadoEm;

    // Pagamento
    private UUID pagoPor;
    private Instant pagoEm;
    private String referenciaPagamento;

    // Auditoria
    private Instant createdAt;
}
