package com.jetski.dashboard.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO for pending records that need attention.
 * Used in the dashboard pending items section.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrosPendentesResponse {

    // Comissões pendentes de pagamento
    private Integer quantidadeComissoesPendentes;
    private BigDecimal totalComissoesPendentes;
    private List<ComissaoPendenteItem> comissoesPendentes;

    // Despesas operacionais pendentes de aprovação
    private Integer quantidadeDespesasPendentes;
    private BigDecimal totalDespesasPendentes;
    private List<DespesaPendenteItem> despesasPendentes;

    // Despesas aprovadas aguardando pagamento
    private Integer quantidadeDespesasAguardandoPagamento;
    private BigDecimal totalDespesasAguardandoPagamento;

    // Fechamentos diários abertos (sem consolidar)
    private Integer quantidadeDiasSemFechamento;
    private List<LocalDate> diasSemFechamento;

    // Fechamentos diários com status "aberto" (não fechados)
    private Integer quantidadeFechamentosAbertos;
    private List<FechamentoAbertoItem> fechamentosAbertos;

    // Manutenções em aberto (despesas de manutenção pendentes de aprovação)
    private Integer quantidadeManutencoesAbertas;
    private BigDecimal totalManutencoesAbertas;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComissaoPendenteItem {
        private UUID id;
        private UUID vendedorId;
        private String vendedorNome;
        private BigDecimal valor;
        private LocalDate dtReferencia;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DespesaPendenteItem {
        private UUID id;
        private LocalDate dtReferencia;
        private String categoria;
        private String descricao;
        private BigDecimal valor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FechamentoAbertoItem {
        private UUID id;
        private LocalDate dtReferencia;
        private BigDecimal totalFaturado;
    }
}
