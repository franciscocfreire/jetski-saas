package com.jetski.fechamento.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for FechamentoDiario (Daily Closure)
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FechamentoDiarioResponse {

    private UUID id;
    private LocalDate dtReferencia;
    private UUID operadorId;

    // Consolidação
    private Integer totalLocacoes;
    private BigDecimal totalFaturado;
    private BigDecimal totalCombustivel;
    private BigDecimal totalComissoes;
    private BigDecimal totalDinheiro;
    private BigDecimal totalCartao;
    private BigDecimal totalPix;

    // Status & Lock
    private String status;
    private Instant dtFechamento;
    private Boolean bloqueado;

    // Metadata
    private String observacoes;
    private String divergenciasJson;
    private Instant createdAt;
    private Instant updatedAt;
}
