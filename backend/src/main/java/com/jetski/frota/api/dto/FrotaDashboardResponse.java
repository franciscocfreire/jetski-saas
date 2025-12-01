package com.jetski.frota.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO: Fleet Dashboard Response
 *
 * Comprehensive dashboard view with fleet KPIs and operational metrics.
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrotaDashboardResponse {

    /**
     * Timestamp when dashboard was generated
     */
    private LocalDateTime timestamp;

    /**
     * Overall fleet summary
     */
    private FrotaSummary summary;

    /**
     * Status breakdown
     */
    private Map<String, Integer> statusDistribution;

    /**
     * Fleet utilization metrics
     */
    private UtilizationMetrics utilization;

    /**
     * Revenue metrics
     */
    private RevenueMetrics revenue;

    /**
     * Maintenance metrics
     */
    private MaintenanceMetrics maintenance;

    /**
     * Top performing jetskis
     */
    private List<JetskiPerformance> topPerformers;

    /**
     * Jetskis requiring attention
     */
    private List<AttentionItem> attentionRequired;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrotaSummary {
        private Integer totalJetskis;
        private Integer disponiveis;
        private Integer emUso;
        private Integer emManutencao;
        private Integer indisponiveis;
        private Double percentualDisponibilidade; // % disponível para locação
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UtilizationMetrics {
        private Double taxaUtilizacao; // % do tempo que jetskis estão em uso
        private Double horasLocadasHoje;
        private Double horasLocadasSemana;
        private Double horasLocadasMes;
        private Double mediaMinutosPorLocacao;
        private Integer locacoesAtivas;
        private Integer locacoesHoje;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueMetrics {
        private BigDecimal receitaHoje;
        private BigDecimal receitaSemana;
        private BigDecimal receitaMes;
        private BigDecimal receitaMediaPorJetski;
        private BigDecimal receitaMediaPorLocacao;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaintenanceMetrics {
        private Integer ordensAbertas;
        private Integer ordensPendentes;
        private Double tempoMedioManutencao; // em horas
        private Integer jetskisProximosManutencao; // próximos de atingir limite de horímetro
        private List<MaintenanceDue> manutencaoVencendo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaintenanceDue {
        private String jetskiId;
        private String serie;
        private String modeloNome;
        private Double horimetroAtual;
        private Double horimetroProximaManutencao;
        private Integer horasRestantes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JetskiPerformance {
        private String jetskiId;
        private String serie;
        private String modeloNome;
        private Integer numeroLocacoes;
        private Double horasLocadas;
        private BigDecimal receitaGerada;
        private Double taxaUtilizacao;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttentionItem {
        private String jetskiId;
        private String serie;
        private String modeloNome;
        private String motivo; // "Manutenção vencida", "Baixa utilização", "Horimetro alto"
        private String descricao;
        private String prioridade; // "ALTA", "MEDIA", "BAIXA"
    }
}
