package com.jetski.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serviço de métricas de negócio customizadas para Jetski SaaS.
 * Expõe métricas relevantes para o dashboard de gestão.
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

    // Contadores - incrementam continuamente
    private Counter locacoesRealizadas;
    private Counter reservasCriadas;
    private Counter checkinsRealizados;
    private Counter checkoutsRealizados;
    private Counter manutencoesCriadas;

    // Timers - medem duração
    private Timer duracaoLocacao;

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

        // Registrar Contadores - métricas que só incrementam
        locacoesRealizadas = Counter.builder("jetski.locacoes.total")
                .description("Total de locações realizadas")
                .tag("tipo", "operacional")
                .register(meterRegistry);

        reservasCriadas = Counter.builder("jetski.reservas.total")
                .description("Total de reservas criadas")
                .tag("tipo", "operacional")
                .register(meterRegistry);

        checkinsRealizados = Counter.builder("jetski.checkins.total")
                .description("Total de check-ins realizados")
                .tag("tipo", "operacional")
                .register(meterRegistry);

        checkoutsRealizados = Counter.builder("jetski.checkouts.total")
                .description("Total de check-outs realizados")
                .tag("tipo", "operacional")
                .register(meterRegistry);

        manutencoesCriadas = Counter.builder("jetski.manutencoes.total")
                .description("Total de ordens de manutenção criadas")
                .tag("tipo", "manutencao")
                .register(meterRegistry);

        // Registrar Timer - mede duração
        duracaoLocacao = Timer.builder("jetski.locacao.duracao")
                .description("Duração das locações")
                .tag("tipo", "operacional")
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

    // Métodos para incrementar contadores (chamados pelos serviços de negócio)

    public void registrarLocacaoRealizada() {
        locacoesRealizadas.increment();
    }

    public void registrarReservaCriada() {
        reservasCriadas.increment();
    }

    public void registrarCheckinRealizado() {
        checkinsRealizados.increment();
    }

    public void registrarCheckoutRealizado() {
        checkoutsRealizados.increment();
    }

    public void registrarManutencaoCriada() {
        manutencoesCriadas.increment();
    }

    public void registrarDuracaoLocacao(long minutos) {
        duracaoLocacao.record(minutos, TimeUnit.MINUTES);
    }
}
