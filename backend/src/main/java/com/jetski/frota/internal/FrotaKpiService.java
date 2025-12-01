package com.jetski.frota.internal;

import com.jetski.frota.api.dto.FrotaDashboardResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service: Fleet KPI Calculator
 *
 * Calculates comprehensive fleet management KPIs and operational metrics.
 * Provides dashboard data for fleet managers and administrators.
 *
 * Key Metrics:
 * - Fleet availability and utilization rates
 * - Revenue per jetski and per rental
 * - Maintenance tracking and alerts
 * - Performance rankings
 * - Attention items (overdue maintenance, low utilization, etc.)
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FrotaKpiService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Preventive maintenance interval in hours (configurable per tenant later)
     */
    private static final double PREVENTIVE_MAINTENANCE_INTERVAL = 50.0;

    /**
     * Warning threshold - alert when within this many hours of maintenance
     */
    private static final double MAINTENANCE_WARNING_THRESHOLD = 10.0;

    /**
     * Generate complete fleet dashboard for a tenant
     *
     * @param tenantId Tenant UUID
     * @return Complete dashboard with all KPIs
     */
    public FrotaDashboardResponse generateDashboard(UUID tenantId) {
        log.info("Generating fleet dashboard for tenant: {}", tenantId);

        return FrotaDashboardResponse.builder()
                .timestamp(LocalDateTime.now())
                .summary(calculateSummary(tenantId))
                .statusDistribution(calculateStatusDistribution(tenantId))
                .utilization(calculateUtilizationMetrics(tenantId))
                .revenue(calculateRevenueMetrics(tenantId))
                .maintenance(calculateMaintenanceMetrics(tenantId))
                .topPerformers(calculateTopPerformers(tenantId, 5))
                .attentionRequired(calculateAttentionItems(tenantId))
                .build();
    }

    /**
     * Calculate fleet summary
     */
    private FrotaDashboardResponse.FrotaSummary calculateSummary(UUID tenantId) {
        String sql = """
            SELECT
                COUNT(*) as total,
                COUNT(*) FILTER (WHERE status = 'DISPONIVEL') as disponiveis,
                COUNT(*) FILTER (WHERE status = 'LOCADO') as em_uso,
                COUNT(*) FILTER (WHERE status = 'MANUTENCAO') as em_manutencao,
                COUNT(*) FILTER (WHERE status = 'INDISPONIVEL') as indisponiveis
            FROM jetski
            WHERE tenant_id = ?
        """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            int total = rs.getInt("total");
            int disponiveis = rs.getInt("disponiveis");
            int emUso = rs.getInt("em_uso");
            int emManutencao = rs.getInt("em_manutencao");
            int indisponiveis = rs.getInt("indisponiveis");

            double percentualDisponibilidade = total > 0
                ? (disponiveis * 100.0 / total)
                : 0.0;

            return FrotaDashboardResponse.FrotaSummary.builder()
                    .totalJetskis(total)
                    .disponiveis(disponiveis)
                    .emUso(emUso)
                    .emManutencao(emManutencao)
                    .indisponiveis(indisponiveis)
                    .percentualDisponibilidade(Math.round(percentualDisponibilidade * 100.0) / 100.0)
                    .build();
        }, tenantId);
    }

    /**
     * Calculate status distribution map
     */
    private Map<String, Integer> calculateStatusDistribution(UUID tenantId) {
        String sql = """
            SELECT status, COUNT(*) as count
            FROM jetski
            WHERE tenant_id = ?
            GROUP BY status
        """;

        List<Map.Entry<String, Integer>> results = jdbcTemplate.query(sql,
            (rs, rowNum) -> Map.entry(rs.getString("status"), rs.getInt("count")),
            tenantId
        );

        return results.stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Calculate utilization metrics
     */
    private FrotaDashboardResponse.UtilizationMetrics calculateUtilizationMetrics(UUID tenantId) {
        LocalDateTime inicioHoje = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime inicioSemana = LocalDateTime.now().minusDays(7);
        LocalDateTime inicioMes = LocalDateTime.now().minusDays(30);

        // Active rentals
        Integer locacoesAtivas = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM locacao WHERE tenant_id = ? AND status IN ('EM_CURSO', 'ATIVA')",
            Integer.class,
            tenantId
        );

        // Rentals today
        Integer locacoesHoje = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM locacao WHERE tenant_id = ? AND data_check_in >= ?",
            Integer.class,
            tenantId, inicioHoje
        );

        // Hours rented (today, week, month)
        String sqlHoras = """
            SELECT
                SUM(CASE WHEN data_check_in >= ? THEN COALESCE(minutos_usados, 0) ELSE 0 END) / 60.0 as hoje,
                SUM(CASE WHEN data_check_in >= ? THEN COALESCE(minutos_usados, 0) ELSE 0 END) / 60.0 as semana,
                SUM(CASE WHEN data_check_in >= ? THEN COALESCE(minutos_usados, 0) ELSE 0 END) / 60.0 as mes,
                AVG(COALESCE(minutos_usados, 0)) as media_minutos
            FROM locacao
            WHERE tenant_id = ? AND status = 'FINALIZADA'
        """;

        return jdbcTemplate.queryForObject(sqlHoras, (rs, rowNum) -> {
            double horasHoje = rs.getDouble("hoje");
            double horasSemana = rs.getDouble("semana");
            double horasMes = rs.getDouble("mes");
            double mediaMinutos = rs.getDouble("media_minutos");

            // Calculate utilization rate: hours rented / available hours
            // Available hours = total jetskis * 24 hours * 30 days
            Integer totalJetskis = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jetski WHERE tenant_id = ?",
                Integer.class,
                tenantId
            );

            double horasDisponiveisMes = totalJetskis != null ? totalJetskis * 24.0 * 30.0 : 1.0;
            double taxaUtilizacao = horasMes > 0
                ? (horasMes / horasDisponiveisMes) * 100.0
                : 0.0;

            return FrotaDashboardResponse.UtilizationMetrics.builder()
                    .taxaUtilizacao(Math.round(taxaUtilizacao * 100.0) / 100.0)
                    .horasLocadasHoje(Math.round(horasHoje * 100.0) / 100.0)
                    .horasLocadasSemana(Math.round(horasSemana * 100.0) / 100.0)
                    .horasLocadasMes(Math.round(horasMes * 100.0) / 100.0)
                    .mediaMinutosPorLocacao(Math.round(mediaMinutos * 100.0) / 100.0)
                    .locacoesAtivas(locacoesAtivas != null ? locacoesAtivas : 0)
                    .locacoesHoje(locacoesHoje != null ? locacoesHoje : 0)
                    .build();
        }, inicioHoje, inicioSemana, inicioMes, tenantId);
    }

    /**
     * Calculate revenue metrics
     */
    private FrotaDashboardResponse.RevenueMetrics calculateRevenueMetrics(UUID tenantId) {
        LocalDateTime inicioHoje = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime inicioSemana = LocalDateTime.now().minusDays(7);
        LocalDateTime inicioMes = LocalDateTime.now().minusDays(30);

        String sql = """
            SELECT
                SUM(CASE WHEN data_check_in >= ? THEN COALESCE(valor_total, 0) ELSE 0 END) as receita_hoje,
                SUM(CASE WHEN data_check_in >= ? THEN COALESCE(valor_total, 0) ELSE 0 END) as receita_semana,
                SUM(CASE WHEN data_check_in >= ? THEN COALESCE(valor_total, 0) ELSE 0 END) as receita_mes,
                AVG(COALESCE(valor_total, 0)) as media_por_locacao,
                COUNT(*) as total_locacoes
            FROM locacao
            WHERE tenant_id = ? AND status = 'FINALIZADA'
        """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            BigDecimal receitaHoje = rs.getBigDecimal("receita_hoje");
            BigDecimal receitaSemana = rs.getBigDecimal("receita_semana");
            BigDecimal receitaMes = rs.getBigDecimal("receita_mes");
            BigDecimal mediaPorLocacao = rs.getBigDecimal("media_por_locacao");

            // Revenue per jetski = total revenue / number of jetskis
            Integer totalJetskis = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jetski WHERE tenant_id = ?",
                Integer.class,
                tenantId
            );

            BigDecimal receitaPorJetski = receitaMes != null && totalJetskis != null && totalJetskis > 0
                ? receitaMes.divide(BigDecimal.valueOf(totalJetskis), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            return FrotaDashboardResponse.RevenueMetrics.builder()
                    .receitaHoje(receitaHoje != null ? receitaHoje : BigDecimal.ZERO)
                    .receitaSemana(receitaSemana != null ? receitaSemana : BigDecimal.ZERO)
                    .receitaMes(receitaMes != null ? receitaMes : BigDecimal.ZERO)
                    .receitaMediaPorJetski(receitaPorJetski)
                    .receitaMediaPorLocacao(mediaPorLocacao != null ? mediaPorLocacao : BigDecimal.ZERO)
                    .build();
        }, inicioHoje, inicioSemana, inicioMes, tenantId);
    }

    /**
     * Calculate maintenance metrics
     */
    private FrotaDashboardResponse.MaintenanceMetrics calculateMaintenanceMetrics(UUID tenantId) {
        // Count open maintenance orders
        Integer ordensAbertas = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM os_manutencao WHERE tenant_id = ? AND status IN ('ABERTA', 'EM_ANDAMENTO')",
            Integer.class,
            tenantId
        );

        Integer ordensPendentes = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM os_manutencao WHERE tenant_id = ? AND status = 'PENDENTE'",
            Integer.class,
            tenantId
        );

        // Average maintenance time in hours
        Double tempoMedioManutencao = jdbcTemplate.queryForObject(
            """
            SELECT AVG(EXTRACT(EPOCH FROM (dt_conclusao - dt_abertura)) / 3600.0)
            FROM os_manutencao
            WHERE tenant_id = ? AND status = 'CONCLUIDA' AND dt_conclusao IS NOT NULL
            """,
            Double.class,
            tenantId
        );

        // Jetskis approaching maintenance (within warning threshold)
        List<FrotaDashboardResponse.MaintenanceDue> manutencaoVencendo =
            calculateJetskisNeedingMaintenance(tenantId);

        return FrotaDashboardResponse.MaintenanceMetrics.builder()
                .ordensAbertas(ordensAbertas != null ? ordensAbertas : 0)
                .ordensPendentes(ordensPendentes != null ? ordensPendentes : 0)
                .tempoMedioManutencao(tempoMedioManutencao != null ? Math.round(tempoMedioManutencao * 100.0) / 100.0 : 0.0)
                .jetskisProximosManutencao(manutencaoVencendo.size())
                .manutencaoVencendo(manutencaoVencendo)
                .build();
    }

    /**
     * Calculate jetskis needing maintenance based on hourmeter
     */
    private List<FrotaDashboardResponse.MaintenanceDue> calculateJetskisNeedingMaintenance(UUID tenantId) {
        String sql = """
            SELECT
                j.id as jetski_id,
                j.serie,
                m.nome as modelo_nome,
                j.horimetro_atual,
                COALESCE(last_maint.horimetro_na_manutencao, 0) as ultimo_horimetro_manutencao
            FROM jetski j
            JOIN modelo m ON j.modelo_id = m.id
            LEFT JOIN LATERAL (
                SELECT om.horimetro_abertura as horimetro_na_manutencao
                FROM os_manutencao om
                WHERE om.jetski_id = j.id AND om.status = 'CONCLUIDA'
                ORDER BY om.dt_conclusao DESC
                LIMIT 1
            ) last_maint ON true
            WHERE j.tenant_id = ?
            AND (j.horimetro_atual - COALESCE(last_maint.horimetro_na_manutencao, 0)) >= ?
        """;

        double warningThreshold = PREVENTIVE_MAINTENANCE_INTERVAL - MAINTENANCE_WARNING_THRESHOLD;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            double horimetroAtual = rs.getDouble("horimetro_atual");
            double ultimoHorimetroManutencao = rs.getDouble("ultimo_horimetro_manutencao");
            double horasDesdeManutencao = horimetroAtual - ultimoHorimetroManutencao;
            double horimetroProximaManutencao = ultimoHorimetroManutencao + PREVENTIVE_MAINTENANCE_INTERVAL;
            int horasRestantes = (int) (PREVENTIVE_MAINTENANCE_INTERVAL - horasDesdeManutencao);

            return FrotaDashboardResponse.MaintenanceDue.builder()
                    .jetskiId(rs.getString("jetski_id"))
                    .serie(rs.getString("serie"))
                    .modeloNome(rs.getString("modelo_nome"))
                    .horimetroAtual(Math.round(horimetroAtual * 100.0) / 100.0)
                    .horimetroProximaManutencao(Math.round(horimetroProximaManutencao * 100.0) / 100.0)
                    .horasRestantes(horasRestantes)
                    .build();
        }, tenantId, warningThreshold);
    }

    /**
     * Calculate top performing jetskis
     */
    private List<FrotaDashboardResponse.JetskiPerformance> calculateTopPerformers(UUID tenantId, int limit) {
        LocalDateTime inicioMes = LocalDateTime.now().minusDays(30);

        String sql = """
            SELECT
                j.id as jetski_id,
                j.serie,
                m.nome as modelo_nome,
                COUNT(l.id) as numero_locacoes,
                SUM(COALESCE(l.minutos_usados, 0)) / 60.0 as horas_locadas,
                SUM(COALESCE(l.valor_total, 0)) as receita_gerada
            FROM jetski j
            JOIN modelo m ON j.modelo_id = m.id
            LEFT JOIN locacao l ON l.jetski_id = j.id
                AND l.status = 'FINALIZADA'
                AND l.data_check_in >= ?
            WHERE j.tenant_id = ?
            GROUP BY j.id, j.serie, m.nome
            HAVING COUNT(l.id) > 0
            ORDER BY receita_gerada DESC
            LIMIT ?
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            double horasLocadas = rs.getDouble("horas_locadas");

            // Utilization rate: hours rented / available hours in period (30 days * 24 hours)
            double horasDisponiveis = 30.0 * 24.0;
            double taxaUtilizacao = (horasLocadas / horasDisponiveis) * 100.0;

            return FrotaDashboardResponse.JetskiPerformance.builder()
                    .jetskiId(rs.getString("jetski_id"))
                    .serie(rs.getString("serie"))
                    .modeloNome(rs.getString("modelo_nome"))
                    .numeroLocacoes(rs.getInt("numero_locacoes"))
                    .horasLocadas(Math.round(horasLocadas * 100.0) / 100.0)
                    .receitaGerada(rs.getBigDecimal("receita_gerada"))
                    .taxaUtilizacao(Math.round(taxaUtilizacao * 100.0) / 100.0)
                    .build();
        }, inicioMes, tenantId, limit);
    }

    /**
     * Calculate attention items (jetskis requiring intervention)
     */
    private List<FrotaDashboardResponse.AttentionItem> calculateAttentionItems(UUID tenantId) {
        List<FrotaDashboardResponse.AttentionItem> items = new ArrayList<>();

        // 1. Jetskis with overdue maintenance
        items.addAll(findOverdueMaintenance(tenantId));

        // 2. Jetskis with low utilization (less than 10% in last 30 days)
        items.addAll(findLowUtilization(tenantId));

        // 3. Jetskis in maintenance for too long (more than 7 days)
        items.addAll(findLongMaintenance(tenantId));

        return items;
    }

    private List<FrotaDashboardResponse.AttentionItem> findOverdueMaintenance(UUID tenantId) {
        String sql = """
            SELECT
                j.id as jetski_id,
                j.serie,
                m.nome as modelo_nome,
                j.horimetro_atual,
                COALESCE(last_maint.horimetro_na_manutencao, 0) as ultimo_horimetro_manutencao
            FROM jetski j
            JOIN modelo m ON j.modelo_id = m.id
            LEFT JOIN LATERAL (
                SELECT om.horimetro_abertura as horimetro_na_manutencao
                FROM os_manutencao om
                WHERE om.jetski_id = j.id AND om.status = 'CONCLUIDA'
                ORDER BY om.dt_conclusao DESC
                LIMIT 1
            ) last_maint ON true
            WHERE j.tenant_id = ?
            AND (j.horimetro_atual - COALESCE(last_maint.horimetro_na_manutencao, 0)) >= ?
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            double horasDesdeManutencao = rs.getDouble("horimetro_atual") - rs.getDouble("ultimo_horimetro_manutencao");
            double horasAtrasadas = horasDesdeManutencao - PREVENTIVE_MAINTENANCE_INTERVAL;

            return FrotaDashboardResponse.AttentionItem.builder()
                    .jetskiId(rs.getString("jetski_id"))
                    .serie(rs.getString("serie"))
                    .modeloNome(rs.getString("modelo_nome"))
                    .motivo("Manutenção preventiva vencida")
                    .descricao(String.format("%.1f horas desde última manutenção (limite: %.0f horas). Atrasado em %.1f horas.",
                            horasDesdeManutencao, PREVENTIVE_MAINTENANCE_INTERVAL, horasAtrasadas))
                    .prioridade("ALTA")
                    .build();
        }, tenantId, PREVENTIVE_MAINTENANCE_INTERVAL);
    }

    private List<FrotaDashboardResponse.AttentionItem> findLowUtilization(UUID tenantId) {
        LocalDateTime inicioMes = LocalDateTime.now().minusDays(30);

        String sql = """
            SELECT
                j.id as jetski_id,
                j.serie,
                m.nome as modelo_nome,
                COALESCE(SUM(l.minutos_usados) / 60.0, 0) as horas_locadas
            FROM jetski j
            JOIN modelo m ON j.modelo_id = m.id
            LEFT JOIN locacao l ON l.jetski_id = j.id
                AND l.status = 'FINALIZADA'
                AND l.data_check_in >= ?
            WHERE j.tenant_id = ?
            AND j.status = 'DISPONIVEL'
            GROUP BY j.id, j.serie, m.nome
            HAVING COALESCE(SUM(l.minutos_usados) / 60.0, 0) < 72  -- Less than 10% of 30 days (720 hours)
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            double horasLocadas = rs.getDouble("horas_locadas");
            double taxaUtilizacao = (horasLocadas / (30.0 * 24.0)) * 100.0;

            return FrotaDashboardResponse.AttentionItem.builder()
                    .jetskiId(rs.getString("jetski_id"))
                    .serie(rs.getString("serie"))
                    .modeloNome(rs.getString("modelo_nome"))
                    .motivo("Baixa utilização")
                    .descricao(String.format("Taxa de utilização: %.1f%% (%.1f horas nos últimos 30 dias)",
                            taxaUtilizacao, horasLocadas))
                    .prioridade("MEDIA")
                    .build();
        }, inicioMes, tenantId);
    }

    private List<FrotaDashboardResponse.AttentionItem> findLongMaintenance(UUID tenantId) {
        LocalDateTime limiteManutencao = LocalDateTime.now().minusDays(7);

        String sql = """
            SELECT DISTINCT
                j.id as jetski_id,
                j.serie,
                m.nome as modelo_nome,
                om.dt_abertura,
                om.tipo,
                om.descricao_problema
            FROM jetski j
            JOIN modelo m ON j.modelo_id = m.id
            JOIN os_manutencao om ON om.jetski_id = j.id
            WHERE j.tenant_id = ?
            AND j.status = 'MANUTENCAO'
            AND om.status IN ('ABERTA', 'EM_ANDAMENTO')
            AND om.dt_abertura < ?
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Instant dtAbertura = rs.getTimestamp("dt_abertura").toInstant();
            long diasEmManutencao = java.time.Duration.between(dtAbertura, Instant.now()).toDays();

            return FrotaDashboardResponse.AttentionItem.builder()
                    .jetskiId(rs.getString("jetski_id"))
                    .serie(rs.getString("serie"))
                    .modeloNome(rs.getString("modelo_nome"))
                    .motivo("Manutenção prolongada")
                    .descricao(String.format("Em manutenção há %d dias. Tipo: %s - %s",
                            diasEmManutencao, rs.getString("tipo"), rs.getString("descricao_problema")))
                    .prioridade("ALTA")
                    .build();
        }, tenantId, limiteManutencao);
    }
}
