package com.jetski.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para BusinessMetricsService.
 * Foca em cobertura de branches: null checks, condicionais e exception handling.
 */
@ExtendWith(MockitoExtension.class)
class BusinessMetricsServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MeterRegistry meterRegistry;
    private BusinessMetricsService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new BusinessMetricsService(meterRegistry, jdbcTemplate);
    }

    @Test
    @DisplayName("Should initialize all metrics on service creation")
    void testInitializeMetrics() {
        // Verify gauges are registered
        assertThat(meterRegistry.find("jetski.locacoes.ativas").gauge()).isNotNull();
        assertThat(meterRegistry.find("jetski.reservas.pendentes").gauge()).isNotNull();
        assertThat(meterRegistry.find("jetski.frota.disponiveis").gauge()).isNotNull();
        assertThat(meterRegistry.find("jetski.frota.em_uso").gauge()).isNotNull();
        assertThat(meterRegistry.find("jetski.frota.em_manutencao").gauge()).isNotNull();
        assertThat(meterRegistry.find("jetski.frota.taxa_ocupacao").gauge()).isNotNull();

        // Verify counters are registered
        assertThat(meterRegistry.find("jetski.locacoes.total").counter()).isNotNull();
        assertThat(meterRegistry.find("jetski.reservas.total").counter()).isNotNull();
        assertThat(meterRegistry.find("jetski.checkins.total").counter()).isNotNull();
        assertThat(meterRegistry.find("jetski.checkouts.total").counter()).isNotNull();
        assertThat(meterRegistry.find("jetski.manutencoes.total").counter()).isNotNull();

        // Verify timer is registered
        assertThat(meterRegistry.find("jetski.locacao.duracao").timer()).isNotNull();
    }

    @Test
    @DisplayName("Should update gauge metrics with non-null database results")
    void testAtualizarMetricasGauge_WithNonNullResults() {
        // Mock database queries returning non-null values (using actual SQL from service)
        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM locacao WHERE status IN ('ATIVA', 'EM_ANDAMENTO')"),
            eq(Integer.class)
        )).thenReturn(5);

        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM reserva WHERE status = 'CONFIRMADA' AND data_inicio > NOW()"),
            eq(Integer.class)
        )).thenReturn(10);

        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM jetski WHERE status = 'DISPONIVEL'"),
            eq(Integer.class)
        )).thenReturn(8);

        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM jetski WHERE status = 'EM_USO'"),
            eq(Integer.class)
        )).thenReturn(3);

        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM jetski WHERE status = 'MANUTENCAO'"),
            eq(Integer.class)
        )).thenReturn(2);

        // Execute
        service.atualizarMetricasGauge();

        // Verify gauge values
        Gauge locacoesAtivasGauge = meterRegistry.find("jetski.locacoes.ativas").gauge();
        assertThat(locacoesAtivasGauge).isNotNull();
        assertThat(locacoesAtivasGauge.value()).isEqualTo(5.0);

        Gauge reservasPendentesGauge = meterRegistry.find("jetski.reservas.pendentes").gauge();
        assertThat(reservasPendentesGauge).isNotNull();
        assertThat(reservasPendentesGauge.value()).isEqualTo(10.0);

        Gauge disponiveisGauge = meterRegistry.find("jetski.frota.disponiveis").gauge();
        assertThat(disponiveisGauge).isNotNull();
        assertThat(disponiveisGauge.value()).isEqualTo(8.0);

        Gauge emUsoGauge = meterRegistry.find("jetski.frota.em_uso").gauge();
        assertThat(emUsoGauge).isNotNull();
        assertThat(emUsoGauge.value()).isEqualTo(3.0);

        Gauge emManutencaoGauge = meterRegistry.find("jetski.frota.em_manutencao").gauge();
        assertThat(emManutencaoGauge).isNotNull();
        assertThat(emManutencaoGauge.value()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should handle null results from database queries (null branch)")
    void testAtualizarMetricasGauge_WithNullResults() {
        // Mock database queries returning null - tests the null branch
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(null);

        // Execute
        service.atualizarMetricasGauge();

        // Verify all gauges default to 0 when query returns null
        Gauge locacoesAtivasGauge = meterRegistry.find("jetski.locacoes.ativas").gauge();
        assertThat(locacoesAtivasGauge).isNotNull();
        assertThat(locacoesAtivasGauge.value()).isEqualTo(0.0);

        Gauge reservasPendentesGauge = meterRegistry.find("jetski.reservas.pendentes").gauge();
        assertThat(reservasPendentesGauge).isNotNull();
        assertThat(reservasPendentesGauge.value()).isEqualTo(0.0);

        Gauge disponiveisGauge = meterRegistry.find("jetski.frota.disponiveis").gauge();
        assertThat(disponiveisGauge).isNotNull();
        assertThat(disponiveisGauge.value()).isEqualTo(0.0);

        Gauge emUsoGauge = meterRegistry.find("jetski.frota.em_uso").gauge();
        assertThat(emUsoGauge).isNotNull();
        assertThat(emUsoGauge.value()).isEqualTo(0.0);

        Gauge emManutencaoGauge = meterRegistry.find("jetski.frota.em_manutencao").gauge();
        assertThat(emManutencaoGauge).isNotNull();
        assertThat(emManutencaoGauge.value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should handle database exception gracefully (exception branch)")
    void testAtualizarMetricasGauge_WithDatabaseException() {
        // Mock database throwing exception - tests the catch block
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
            .thenThrow(new DataAccessException("Database connection failed") {});

        // Execute - should not throw exception
        service.atualizarMetricasGauge();

        // Verify service continues working (gauges retain previous values, which are 0 initially)
        Gauge locacoesAtivasGauge = meterRegistry.find("jetski.locacoes.ativas").gauge();
        assertThat(locacoesAtivasGauge).isNotNull();
        assertThat(locacoesAtivasGauge.value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should calculate taxa_ocupacao correctly when total > 0")
    void testCalcularTaxaOcupacao_WithValidTotal() {
        // Setup: 8 disponíveis + 3 em uso = 11 total
        // Taxa = (3 / 11) * 100 = 27.27%
        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM locacao WHERE status IN ('ATIVA', 'EM_ANDAMENTO')"),
            eq(Integer.class)
        )).thenReturn(0);

        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM reserva WHERE status = 'CONFIRMADA' AND data_inicio > NOW()"),
            eq(Integer.class)
        )).thenReturn(0);

        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM jetski WHERE status = 'DISPONIVEL'"),
            eq(Integer.class)
        )).thenReturn(8);

        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM jetski WHERE status = 'EM_USO'"),
            eq(Integer.class)
        )).thenReturn(3);

        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM jetski WHERE status = 'MANUTENCAO'"),
            eq(Integer.class)
        )).thenReturn(0);

        // Execute to update gauges
        service.atualizarMetricasGauge();

        // Verify taxa_ocupacao calculation
        Gauge taxaOcupacaoGauge = meterRegistry.find("jetski.frota.taxa_ocupacao").gauge();
        assertThat(taxaOcupacaoGauge).isNotNull();
        assertThat(taxaOcupacaoGauge.value()).isCloseTo(27.27, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("Should return 0 for taxa_ocupacao when total is 0 (division by zero branch)")
    void testCalcularTaxaOcupacao_WithZeroTotal() {
        // Setup: 0 disponíveis + 0 em uso = 0 total
        // Tests the if (total == 0) branch
        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM locacao WHERE status IN ('ATIVA', 'EM_ANDAMENTO')"),
            eq(Integer.class)
        )).thenReturn(0);

        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM reserva WHERE status = 'CONFIRMADA' AND data_inicio > NOW()"),
            eq(Integer.class)
        )).thenReturn(0);

        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM jetski WHERE status = 'DISPONIVEL'"),
            eq(Integer.class)
        )).thenReturn(0);

        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM jetski WHERE status = 'EM_USO'"),
            eq(Integer.class)
        )).thenReturn(0);

        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM jetski WHERE status = 'MANUTENCAO'"),
            eq(Integer.class)
        )).thenReturn(0);

        // Execute to update gauges
        service.atualizarMetricasGauge();

        // Verify taxa_ocupacao returns 0 to avoid division by zero
        Gauge taxaOcupacaoGauge = meterRegistry.find("jetski.frota.taxa_ocupacao").gauge();
        assertThat(taxaOcupacaoGauge).isNotNull();
        assertThat(taxaOcupacaoGauge.value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should increment locacoes counter")
    void testRegistrarLocacaoRealizada() {
        Counter locacoesCounter = meterRegistry.find("jetski.locacoes.total").counter();
        assertThat(locacoesCounter).isNotNull();
        double before = locacoesCounter.count();

        service.registrarLocacaoRealizada();

        assertThat(locacoesCounter.count()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("Should increment reservas counter")
    void testRegistrarReservaCriada() {
        Counter reservasCounter = meterRegistry.find("jetski.reservas.total").counter();
        assertThat(reservasCounter).isNotNull();
        double before = reservasCounter.count();

        service.registrarReservaCriada();

        assertThat(reservasCounter.count()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("Should increment checkins counter")
    void testRegistrarCheckinRealizado() {
        Counter checkinsCounter = meterRegistry.find("jetski.checkins.total").counter();
        assertThat(checkinsCounter).isNotNull();
        double before = checkinsCounter.count();

        service.registrarCheckinRealizado();

        assertThat(checkinsCounter.count()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("Should increment checkouts counter")
    void testRegistrarCheckoutRealizado() {
        Counter checkoutsCounter = meterRegistry.find("jetski.checkouts.total").counter();
        assertThat(checkoutsCounter).isNotNull();
        double before = checkoutsCounter.count();

        service.registrarCheckoutRealizado();

        assertThat(checkoutsCounter.count()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("Should increment manutencoes counter")
    void testRegistrarManutencaoCriada() {
        Counter manutencoesCounter = meterRegistry.find("jetski.manutencoes.total").counter();
        assertThat(manutencoesCounter).isNotNull();
        double before = manutencoesCounter.count();

        service.registrarManutencaoCriada();

        assertThat(manutencoesCounter.count()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("Should record rental duration in timer")
    void testRegistrarDuracaoLocacao() {
        Timer duracaoTimer = meterRegistry.find("jetski.locacao.duracao").timer();
        assertThat(duracaoTimer).isNotNull();
        long before = duracaoTimer.count();

        service.registrarDuracaoLocacao(120); // 120 minutes

        assertThat(duracaoTimer.count()).isEqualTo(before + 1);
        assertThat(duracaoTimer.totalTime(TimeUnit.MINUTES)).isGreaterThanOrEqualTo(120.0);
    }
}
