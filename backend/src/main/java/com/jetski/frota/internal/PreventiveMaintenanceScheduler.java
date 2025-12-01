package com.jetski.frota.internal;

import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.JetskiStatus;
import com.jetski.manutencao.api.ManutencaoPublicService;
import com.jetski.manutencao.domain.OSManutencao;
import com.jetski.manutencao.domain.OSManutencaoPrioridade;
import com.jetski.manutencao.domain.OSManutencaoTipo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Preventive Maintenance Scheduler
 *
 * Automatically monitors jetski fleet and creates preventive maintenance orders
 * when jetskis reach their maintenance threshold based on hourmeter readings.
 *
 * Business Rules:
 * - RN07: Preventive maintenance required every 50 hours (configurable)
 * - Auto-create PREVENTIVA OS when threshold is reached
 * - Send notifications to GERENTE and MECANICO roles
 * - Skip jetskis already in maintenance
 *
 * Schedule: Runs daily at 6:00 AM
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PreventiveMaintenanceScheduler {

    private final JdbcTemplate jdbcTemplate;
    private final ManutencaoPublicService manutencaoPublicService;

    /**
     * Preventive maintenance interval in hours
     * TODO: Make this configurable per tenant via application.properties or tenant config
     */
    private static final double PREVENTIVE_MAINTENANCE_INTERVAL = 50.0;

    /**
     * Early warning threshold - create OS when within this many hours of maintenance
     * This allows managers to plan ahead before the jetski is urgently needed
     */
    private static final double EARLY_WARNING_HOURS = 5.0;

    /**
     * Scheduled task: Check for jetskis needing preventive maintenance
     *
     * Runs: Daily at 6:00 AM (cron: "0 0 6 * * *")
     * Timezone: System default (can be configured via application.properties)
     *
     * Process:
     * 1. Query all jetskis across all tenants
     * 2. Calculate hours since last maintenance
     * 3. If >= threshold, auto-create preventive maintenance OS
     * 4. Send notification to managers/mechanics
     *
     * Note: Uses PostgreSQL RLS to ensure tenant isolation in queries
     */
    @Scheduled(cron = "0 0 6 * * *") // Every day at 6:00 AM
    @Transactional
    public void checkPreventiveMaintenance() {
        log.info("Starting preventive maintenance check (scheduled job)");

        try {
            List<JetskiMaintenanceInfo> jetskisNeedingMaintenance = findJetskisNeedingMaintenance();

            if (jetskisNeedingMaintenance.isEmpty()) {
                log.info("No jetskis require preventive maintenance at this time");
                return;
            }

            log.info("Found {} jetskis requiring preventive maintenance", jetskisNeedingMaintenance.size());

            int ordersCreated = 0;
            for (JetskiMaintenanceInfo info : jetskisNeedingMaintenance) {
                try {
                    createPreventiveMaintenanceOrder(info);
                    ordersCreated++;
                } catch (Exception e) {
                    log.error("Failed to create preventive maintenance OS for jetski {}: {}",
                            info.jetskiId, e.getMessage(), e);
                    // Continue processing other jetskis even if one fails
                }
            }

            log.info("Preventive maintenance check completed: {} orders created", ordersCreated);

        } catch (Exception e) {
            log.error("Preventive maintenance scheduler failed", e);
            // Don't throw - let the scheduler continue on next execution
        }
    }

    /**
     * Find jetskis that need preventive maintenance based on hourmeter
     *
     * Criteria:
     * - Hours since last maintenance >= (INTERVAL - EARLY_WARNING)
     * - Not already in MANUTENCAO status
     * - Not already have an open PREVENTIVA OS
     *
     * @return List of jetskis needing maintenance
     */
    private List<JetskiMaintenanceInfo> findJetskisNeedingMaintenance() {
        double threshold = PREVENTIVE_MAINTENANCE_INTERVAL - EARLY_WARNING_HOURS;

        String sql = """
            WITH last_maintenance AS (
                SELECT DISTINCT ON (om.jetski_id)
                    om.jetski_id,
                    om.horimetro_conclusao as ultimo_horimetro
                FROM os_manutencao om
                WHERE om.status = 'concluida'
                  AND om.tipo = 'preventiva'
                  AND om.horimetro_conclusao IS NOT NULL
                ORDER BY om.jetski_id, om.dt_conclusao DESC
            )
            SELECT
                j.id as jetski_id,
                j.tenant_id,
                j.serie,
                j.horimetro_atual,
                COALESCE(lm.ultimo_horimetro, 0) as ultimo_horimetro_manutencao,
                m.nome as modelo_nome
            FROM jetski j
            JOIN modelo m ON j.modelo_id = m.id
            LEFT JOIN last_maintenance lm ON lm.jetski_id = j.id
            WHERE j.status != 'manutencao'
              AND (j.horimetro_atual - COALESCE(lm.ultimo_horimetro, 0)) >= ?
              AND NOT EXISTS (
                  SELECT 1 FROM os_manutencao om2
                  WHERE om2.jetski_id = j.id
                    AND om2.tipo = 'preventiva'
                    AND om2.status IN ('aberta', 'em_andamento', 'aguardando_pecas')
              )
            ORDER BY (j.horimetro_atual - COALESCE(lm.ultimo_horimetro, 0)) DESC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            double horimetroAtual = rs.getDouble("horimetro_atual");
            double ultimoHorimetroManutencao = rs.getDouble("ultimo_horimetro_manutencao");
            double horasDesdeManutencao = horimetroAtual - ultimoHorimetroManutencao;

            return new JetskiMaintenanceInfo(
                    UUID.fromString(rs.getString("jetski_id")),
                    UUID.fromString(rs.getString("tenant_id")),
                    rs.getString("serie"),
                    rs.getString("modelo_nome"),
                    BigDecimal.valueOf(horimetroAtual),
                    BigDecimal.valueOf(ultimoHorimetroManutencao),
                    horasDesdeManutencao
            );
        }, threshold);
    }

    /**
     * Create preventive maintenance order for a jetski
     *
     * @param info Jetski maintenance information
     */
    private void createPreventiveMaintenanceOrder(JetskiMaintenanceInfo info) {
        log.info("Creating preventive maintenance OS for jetski {} (serie: {}, {} hours since last maintenance)",
                info.jetskiId, info.serie, String.format("%.1f", info.horasDesdeManutencao));

        // Determine priority based on how overdue the maintenance is
        OSManutencaoPrioridade prioridade;
        if (info.horasDesdeManutencao >= PREVENTIVE_MAINTENANCE_INTERVAL + 10.0) {
            prioridade = OSManutencaoPrioridade.ALTA; // 10+ hours overdue
        } else if (info.horasDesdeManutencao >= PREVENTIVE_MAINTENANCE_INTERVAL) {
            prioridade = OSManutencaoPrioridade.MEDIA; // At or past threshold
        } else {
            prioridade = OSManutencaoPrioridade.BAIXA; // Within early warning period
        }

        String descricao = String.format(
                "Manutenção preventiva automática - %s\n\n" +
                "Horímetro atual: %.1fh\n" +
                "Última manutenção: %.1fh\n" +
                "Horas desde última manutenção: %.1fh\n" +
                "Intervalo recomendado: %.0fh\n\n" +
                "Serviços recomendados:\n" +
                "- Troca de óleo\n" +
                "- Verificação de velas\n" +
                "- Inspeção do casco\n" +
                "- Verificação do sistema de refrigeração\n" +
                "- Limpeza do filtro de combustível",
                info.modeloNome,
                info.horimetroAtual.doubleValue(),
                info.ultimoHorimetroManutencao.doubleValue(),
                info.horasDesdeManutencao,
                PREVENTIVE_MAINTENANCE_INTERVAL
        );

        OSManutencao os = OSManutencao.builder()
                .tenantId(info.tenantId)
                .jetskiId(info.jetskiId)
                .tipo(OSManutencaoTipo.PREVENTIVA)
                .prioridade(prioridade)
                .descricaoProblema(descricao)
                .horimetroAbertura(info.horimetroAtual)
                .dtAbertura(Instant.now())
                .observacoes("OS criada automaticamente pelo sistema de manutenção preventiva")
                .build();

        // Create the maintenance order
        // Note: This will automatically set jetski status to MANUTENCAO (RN06)
        OSManutencao created = manutencaoPublicService.createOrder(os);

        log.info("Preventive maintenance OS created: id={}, jetski={}, prioridade={}",
                created.getId(), created.getJetskiId(), created.getPrioridade());

        // TODO: Send notification to managers and mechanics
        // sendMaintenanceNotification(created);
    }

    /**
     * DTO for jetski maintenance information
     */
    private record JetskiMaintenanceInfo(
            UUID jetskiId,
            UUID tenantId,
            String serie,
            String modeloNome,
            BigDecimal horimetroAtual,
            BigDecimal ultimoHorimetroManutencao,
            double horasDesdeManutencao
    ) {}

    // TODO: Implement notification service
    // private void sendMaintenanceNotification(OSManutencao os) {
    //     // Send email/push notification to:
    //     // - GERENTE role users of the tenant
    //     // - MECANICO role users of the tenant
    //     // Include: jetski info, hours since maintenance, priority, OS ID
    // }
}
