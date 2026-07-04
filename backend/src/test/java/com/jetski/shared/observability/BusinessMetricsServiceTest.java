package com.jetski.shared.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para BusinessMetricsService.
 * Foca em cobertura de branches: null checks, condicionais e exception handling.
 *
 * <p>Contadores de eventos não vivem mais aqui — ver MetricsEventListenerTest
 * (módulo metrics).
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
    @DisplayName("Should initialize all gauges on service creation")
    void testInitializeMetrics() {
        assertThat(meterRegistry.find("jetski.locacoes.ativas").gauge()).isNotNull();
        assertThat(meterRegistry.find("jetski.reservas.pendentes").gauge()).isNotNull();
        assertThat(meterRegistry.find("jetski.frota.disponiveis").gauge()).isNotNull();
        assertThat(meterRegistry.find("jetski.frota.em_uso").gauge()).isNotNull();
        assertThat(meterRegistry.find("jetski.frota.em_manutencao").gauge()).isNotNull();
        assertThat(meterRegistry.find("jetski.frota.taxa_ocupacao").gauge()).isNotNull();
    }

    @Test
    @DisplayName("Should update gauge metrics with non-null database results")
    void testAtualizarMetricasGauge_WithNonNullResults() {
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

        service.atualizarMetricasGauge();

        assertThat(gaugeValue("jetski.locacoes.ativas")).isEqualTo(5.0);
        assertThat(gaugeValue("jetski.reservas.pendentes")).isEqualTo(10.0);
        assertThat(gaugeValue("jetski.frota.disponiveis")).isEqualTo(8.0);
        assertThat(gaugeValue("jetski.frota.em_uso")).isEqualTo(3.0);
        assertThat(gaugeValue("jetski.frota.em_manutencao")).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should handle null results from database queries (null branch)")
    void testAtualizarMetricasGauge_WithNullResults() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(null);

        service.atualizarMetricasGauge();

        assertThat(gaugeValue("jetski.locacoes.ativas")).isEqualTo(0.0);
        assertThat(gaugeValue("jetski.reservas.pendentes")).isEqualTo(0.0);
        assertThat(gaugeValue("jetski.frota.disponiveis")).isEqualTo(0.0);
        assertThat(gaugeValue("jetski.frota.em_uso")).isEqualTo(0.0);
        assertThat(gaugeValue("jetski.frota.em_manutencao")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should handle database exception gracefully (exception branch)")
    void testAtualizarMetricasGauge_WithDatabaseException() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
            .thenThrow(new DataAccessException("Database connection failed") {});

        // Execute - should not throw exception
        service.atualizarMetricasGauge();

        assertThat(gaugeValue("jetski.locacoes.ativas")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should calculate taxa_ocupacao correctly when total > 0")
    void testCalcularTaxaOcupacao_WithValidTotal() {
        // 8 disponíveis + 3 em uso = 11 total → taxa = (3 / 11) * 100 = 27.27%
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM jetski WHERE status = 'DISPONIVEL'"),
            eq(Integer.class)
        )).thenReturn(8);
        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM jetski WHERE status = 'EM_USO'"),
            eq(Integer.class)
        )).thenReturn(3);

        service.atualizarMetricasGauge();

        assertThat(gaugeValue("jetski.frota.taxa_ocupacao"))
            .isCloseTo(27.27, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("Should return 0 for taxa_ocupacao when total is 0 (division by zero branch)")
    void testCalcularTaxaOcupacao_WithZeroTotal() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);

        service.atualizarMetricasGauge();

        assertThat(gaugeValue("jetski.frota.taxa_ocupacao")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should expose credit balance per tenant from the ledger")
    @SuppressWarnings("unchecked")
    void testAtualizarSaldoCreditos() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        when(jdbcTemplate.query(contains("credito_lancamento"), any(RowMapper.class)))
            .thenReturn(List.of(
                MultiGauge.Row.of(Tags.of("tenant_id", "11111111-1111-1111-1111-111111111111"), 42),
                MultiGauge.Row.of(Tags.of("tenant_id", "22222222-2222-2222-2222-222222222222"), 0)
            ));

        service.atualizarMetricasGauge();

        Gauge saldoTenant1 = meterRegistry.find("jetski.creditos.saldo")
            .tag("tenant_id", "11111111-1111-1111-1111-111111111111").gauge();
        assertThat(saldoTenant1).isNotNull();
        assertThat(saldoTenant1.value()).isEqualTo(42.0);

        Gauge saldoTenant2 = meterRegistry.find("jetski.creditos.saldo")
            .tag("tenant_id", "22222222-2222-2222-2222-222222222222").gauge();
        assertThat(saldoTenant2).isNotNull();
        assertThat(saldoTenant2.value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should handle ledger query failure without breaking other gauges")
    @SuppressWarnings("unchecked")
    void testAtualizarSaldoCreditos_WithDatabaseException() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(7);
        when(jdbcTemplate.query(contains("credito_lancamento"), any(RowMapper.class)))
            .thenThrow(new DataAccessException("relation credito_lancamento does not exist") {});

        // Execute - should not throw exception
        service.atualizarMetricasGauge();

        // Other gauges still updated
        assertThat(gaugeValue("jetski.locacoes.ativas")).isEqualTo(7.0);
    }

    private double gaugeValue(String name) {
        Gauge gauge = meterRegistry.find(name).gauge();
        assertThat(gauge).isNotNull();
        return gauge.value();
    }
}
