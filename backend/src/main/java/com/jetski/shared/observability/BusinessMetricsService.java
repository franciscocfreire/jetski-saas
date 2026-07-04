package com.jetski.shared.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serviço de métricas de negócio customizadas para Jetski SaaS.
 * Expõe gauges de estado atual consultando o banco periodicamente.
 *
 * <p>Contadores de eventos (check-ins, reservas, emissões…) NÃO vivem aqui —
 * são incrementados por evento de domínio no {@code MetricsEventListener}
 * (módulo {@code metrics}) via {@link BusinessMetrics}, com tag de tenant.
 */
@Service
@Slf4j
public class BusinessMetricsService {

    private final MeterRegistry meterRegistry;
    private final JdbcTemplate jdbcTemplate;

    // Gauges - valores que variam ao longo do tempo
    private final AtomicInteger locacoesAtivas = new AtomicInteger(0);
    private final AtomicInteger reservasPendentes = new AtomicInteger(0);
    private final AtomicInteger jetskisDisponiveis = new AtomicInteger(0);
    private final AtomicInteger jetskisEmUso = new AtomicInteger(0);
    private final AtomicInteger jetskisEmManutencao = new AtomicInteger(0);
    private final Map<String, AtomicInteger> jetskisPorModelo = new ConcurrentHashMap<>();

    // Saldo de créditos de emissão por tenant (ledger append-only)
    private MultiGauge creditosSaldo;

    public BusinessMetricsService(MeterRegistry meterRegistry, JdbcTemplate jdbcTemplate) {
        this.meterRegistry = meterRegistry;
        this.jdbcTemplate = jdbcTemplate;

        initializeMetrics();
    }

    private void initializeMetrics() {
        log.info("Initializing business metrics...");

        // Registrar Gauges - métricas que podem subir/descer
        Gauge.builder("jetski.locacoes.ativas", locacoesAtivas, AtomicInteger::get)
                .description("Número de locações ativas no momento")
                .tag("tipo", "operacional")
                .register(meterRegistry);

        Gauge.builder("jetski.reservas.pendentes", reservasPendentes, AtomicInteger::get)
                .description("Número de reservas pendentes")
                .tag("tipo", "operacional")
                .register(meterRegistry);

        Gauge.builder("jetski.frota.disponiveis", jetskisDisponiveis, AtomicInteger::get)
                .description("Número de jet skis disponíveis para locação")
                .tag("tipo", "frota")
                .register(meterRegistry);

        Gauge.builder("jetski.frota.em_uso", jetskisEmUso, AtomicInteger::get)
                .description("Número de jet skis em uso")
                .tag("tipo", "frota")
                .register(meterRegistry);

        Gauge.builder("jetski.frota.em_manutencao", jetskisEmManutencao, AtomicInteger::get)
                .description("Número de jet skis em manutenção")
                .tag("tipo", "frota")
                .register(meterRegistry);

        // Taxa de ocupação da frota (calculada)
        Gauge.builder("jetski.frota.taxa_ocupacao", this, BusinessMetricsService::calcularTaxaOcupacao)
                .description("Taxa de ocupação da frota (%)")
                .tag("tipo", "frota")
                .register(meterRegistry);

        // Saldo de créditos de emissão por tenant (linhas registradas a cada refresh)
        creditosSaldo = MultiGauge.builder("jetski.creditos.saldo")
                .description("Saldo de créditos de emissão por tenant")
                .register(meterRegistry);

        log.info("Business metrics initialized successfully");
    }

    /**
     * Atualiza as métricas de gauge consultando o banco de dados.
     * Executado a cada 30 segundos.
     */
    @Scheduled(fixedRate = 30000)
    public void atualizarMetricasGauge() {
        try {
            // Atualizar locações ativas
            Integer locacoesAtivasCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM locacao WHERE status IN ('ATIVA', 'EM_ANDAMENTO')",
                Integer.class
            );
            locacoesAtivas.set(locacoesAtivasCount != null ? locacoesAtivasCount : 0);

            // Atualizar reservas pendentes
            Integer reservasPendentesCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reserva WHERE status = 'CONFIRMADA' AND data_inicio > NOW()",
                Integer.class
            );
            reservasPendentes.set(reservasPendentesCount != null ? reservasPendentesCount : 0);

            // Atualizar jet skis por status
            Integer disponiveisCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jetski WHERE status = 'DISPONIVEL'",
                Integer.class
            );
            jetskisDisponiveis.set(disponiveisCount != null ? disponiveisCount : 0);

            Integer emUsoCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jetski WHERE status = 'EM_USO'",
                Integer.class
            );
            jetskisEmUso.set(emUsoCount != null ? emUsoCount : 0);

            Integer emManutencaoCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jetski WHERE status = 'MANUTENCAO'",
                Integer.class
            );
            jetskisEmManutencao.set(emManutencaoCount != null ? emManutencaoCount : 0);

            log.debug("Business metrics updated - Locações ativas: {}, Reservas pendentes: {}, Jet skis disponíveis: {}",
                    locacoesAtivas.get(), reservasPendentes.get(), jetskisDisponiveis.get());

        } catch (Exception e) {
            log.error("Error updating gauge metrics", e);
        }

        atualizarSaldoCreditos();
    }

    /**
     * Atualiza o gauge de saldo de créditos por tenant a partir do ledger.
     * Try/catch próprio: ambiente sem a tabela não derruba os demais gauges.
     */
    private void atualizarSaldoCreditos() {
        try {
            List<MultiGauge.Row<?>> rows = jdbcTemplate.query(
                "SELECT tenant_id::text AS tenant_id, COALESCE(SUM(quantidade), 0) AS saldo " +
                "FROM credito_lancamento GROUP BY tenant_id",
                (rs, i) -> MultiGauge.Row.of(
                    Tags.of("tenant_id", rs.getString("tenant_id")), rs.getInt("saldo"))
            );
            creditosSaldo.register(rows, true);
        } catch (Exception e) {
            log.error("Error updating credit balance gauge", e);
        }
    }

    /**
     * Calcula a taxa de ocupação da frota.
     */
    private double calcularTaxaOcupacao() {
        int total = jetskisDisponiveis.get() + jetskisEmUso.get();
        if (total == 0) {
            return 0.0;
        }
        return (jetskisEmUso.get() * 100.0) / total;
    }

}
