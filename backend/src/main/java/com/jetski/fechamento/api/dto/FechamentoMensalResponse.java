package com.jetski.fechamento.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for FechamentoMensal (Monthly Closure)
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FechamentoMensalResponse {

    private UUID id;
    private Integer ano;
    private Integer mes;
    private UUID operadorId;

    // Consolidação
    private Integer totalLocacoes;
    private BigDecimal totalFaturado;
    private BigDecimal totalCustos;
    private BigDecimal totalComissoes;
    private BigDecimal totalManutencoes;
    private BigDecimal resultadoLiquido;

    // Status & Lock
    private String status;
    private Instant dtFechamento;
    private Boolean bloqueado;

    // Metadata
    private String observacoes;
    private String relatorioUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
