package com.jetski.fechamento.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO para divergências detectadas em fechamentos.
 *
 * <p>Retorna informações sobre fechamentos cujos valores armazenados
 * divergem dos valores atuais das locações (após edições).</p>
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DivergenciaResponse {

    private UUID fechamentoId;
    private LocalDate dtReferencia;
    private String status;

    // Valores armazenados (da consolidação anterior)
    private Integer totalLocacoesArmazenado;
    private BigDecimal totalFaturadoArmazenado;
    private BigDecimal totalCombustivelArmazenado;
    private BigDecimal totalComissoesArmazenado;

    // Valores atuais (recalculados das locações)
    private Integer totalLocacoesAtual;
    private BigDecimal totalFaturadoAtual;
    private BigDecimal totalCombustivelAtual;
    private BigDecimal totalComissoesAtual;

    // Diferenças
    private Integer diferencaLocacoes;
    private BigDecimal diferencaFaturado;
    private BigDecimal diferencaCombustivel;
    private BigDecimal diferencaComissoes;

    // Lista detalhada de locações alteradas
    private List<LocacaoAlterada> locacoesAlteradas;

    private Instant ultimaConsolidacao;
    private String mensagem;
}
