package com.jetski.metrics.internal;

import com.jetski.creditos.domain.event.CreditoLancadoEvent;
import com.jetski.locacoes.event.CheckInEvent;
import com.jetski.locacoes.event.CheckOutEvent;
import com.jetski.locacoes.event.DocumentoPreviewGeradoEvent;
import com.jetski.locacoes.event.GruEmitidaEvent;
import com.jetski.reservas.domain.event.DocumentosEmitidosEvent;
import com.jetski.reservas.domain.event.PagamentoConfirmadoEvent;
import com.jetski.reservas.domain.event.PagamentoRecusadoEvent;
import com.jetski.reservas.domain.event.ReservationCancelledEvent;
import com.jetski.reservas.domain.event.ReservationConfirmedEvent;
import com.jetski.reservas.domain.event.ReservationCreatedEvent;
import com.jetski.shared.observability.BusinessMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários do MetricsEventListener: cada evento de domínio deve
 * incrementar o medidor correspondente com a tag tenant_id.
 */
class MetricsEventListenerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String TENANT_TAG = TENANT.toString();

    private MeterRegistry registry;
    private MetricsEventListener listener;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        listener = new MetricsEventListener(new BusinessMetrics(registry));
    }

    @Test
    @DisplayName("CheckInEvent incrementa jetski.rental.checkin do tenant")
    void testOnCheckIn() {
        listener.onCheckIn(new CheckInEvent(TENANT, UUID.randomUUID(), UUID.randomUUID(),
            null, UUID.randomUUID(), UUID.randomUUID(), 100, LocalDateTime.now(),
            CheckInEvent.CheckInTipo.WALK_IN));

        assertThat(counterValue("jetski.rental.checkin")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("CheckOutEvent incrementa checkout, duração e receita")
    void testOnCheckOut() {
        listener.onCheckOut(new CheckOutEvent(TENANT, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), 110, 90, new BigDecimal("350.00"),
            LocalDateTime.now()));

        assertThat(counterValue("jetski.rental.checkout")).isEqualTo(1.0);
        assertThat(registry.get("jetski.rental.duration").tag("tenant_id", TENANT_TAG)
            .timer().totalTime(TimeUnit.MINUTES)).isEqualTo(90.0);
        assertThat(registry.get("jetski.rental.valor").tag("tenant_id", TENANT_TAG)
            .summary().totalAmount()).isEqualTo(350.0);
    }

    @Test
    @DisplayName("CheckOutEvent tolera duração e valor nulos")
    void testOnCheckOut_NullValues() {
        listener.onCheckOut(new CheckOutEvent(TENANT, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), null, null, null, LocalDateTime.now()));

        assertThat(counterValue("jetski.rental.checkout")).isEqualTo(1.0);
        assertThat(registry.find("jetski.rental.duration").timer()).isNull();
        assertThat(registry.find("jetski.rental.valor").summary()).isNull();
    }

    @Test
    @DisplayName("Ciclo de reserva: created, confirmed e cancelled")
    void testReservationLifecycle() {
        listener.onReservationCreated(new ReservationCreatedEvent(TENANT, UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
            LocalDateTime.now(), LocalDateTime.now().plusHours(2), false, Instant.now()));
        listener.onReservationConfirmed(new ReservationConfirmedEvent(TENANT, UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
        listener.onReservationCancelled(new ReservationCancelledEvent(TENANT, UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), "cliente desistiu", Instant.now()));

        for (String evento : new String[]{"criada", "confirmada", "cancelada"}) {
            assertThat(registry.get("jetski.reserva")
                .tag("tenant_id", TENANT_TAG).tag("evento", evento)
                .counter().count()).isEqualTo(1.0);
        }
        // motivo livre NÃO vira tag (cardinalidade)
        assertThat(registry.get("jetski.reserva").tag("evento", "cancelada")
            .counter().getId().getTag("reason")).isNull();
    }

    @Test
    @DisplayName("Pagamentos: confirmado (com tipo e valor) e recusado")
    void testPagamentos() {
        listener.onPagamentoConfirmado(new PagamentoConfirmadoEvent(TENANT, UUID.randomUUID(),
            "PIX", new BigDecimal("150.00"), UUID.randomUUID(), Instant.now()));
        listener.onPagamentoRecusado(new PagamentoRecusadoEvent(TENANT, UUID.randomUUID(),
            "comprovante ilegível", UUID.randomUUID(), Instant.now()));

        assertThat(registry.get("jetski.pagamento.valor")
            .tag("tenant_id", TENANT_TAG).tag("tipo", "PIX")
            .summary().totalAmount()).isEqualTo(150.0);
        assertThat(counterValue("jetski.pagamento.recusado")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Emissões: DOCUMENTO, GRU e PREVIA com tag tipo")
    void testEmissoes() {
        listener.onDocumentosEmitidos(new DocumentosEmitidosEvent(TENANT, UUID.randomUUID(),
            UUID.randomUUID(), "email", UUID.randomUUID(), null, Instant.now()));
        listener.onGruEmitida(new GruEmitidaEvent(TENANT, UUID.randomUUID(),
            UUID.randomUUID(), "PIX", Instant.now()));
        listener.onDocumentoPreviewGerado(new DocumentoPreviewGeradoEvent(TENANT,
            UUID.randomUUID(), "download", Instant.now()));

        for (String tipo : new String[]{"DOCUMENTO", "GRU", "PREVIA"}) {
            assertThat(registry.get("jetski.emissao")
                .tag("tenant_id", TENANT_TAG).tag("tipo", tipo)
                .counter().count()).isEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("CreditoLancadoEvent registra movimento em valor absoluto sob o tipo")
    void testCreditoLancado() {
        listener.onCreditoLancado(new CreditoLancadoEvent(TENANT, "ADESAO", 20, 20,
            "Créditos de adesão", null, Instant.now()));
        listener.onCreditoLancado(new CreditoLancadoEvent(TENANT, "AJUSTE", -5, 15,
            "Ajuste manual", UUID.randomUUID(), Instant.now()));

        assertThat(registry.get("jetski.creditos.movimento")
            .tag("tenant_id", TENANT_TAG).tag("tipo", "ADESAO")
            .counter().count()).isEqualTo(20.0);
        assertThat(registry.get("jetski.creditos.movimento")
            .tag("tenant_id", TENANT_TAG).tag("tipo", "AJUSTE")
            .counter().count()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("Nomes expostos no formato Prometheus batem com o contrato dos dashboards")
    void testPrometheusExposedNames() {
        // Os dashboards do Grafana (visao-tenant.json) consultam por estes nomes;
        // se este teste quebrar, os painéis quebram junto.
        var prometheus = new io.micrometer.prometheusmetrics.PrometheusMeterRegistry(
            io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT);
        var promListener = new MetricsEventListener(new BusinessMetrics(prometheus));

        promListener.onCheckIn(new CheckInEvent(TENANT, UUID.randomUUID(), UUID.randomUUID(),
            null, UUID.randomUUID(), UUID.randomUUID(), 100, LocalDateTime.now(),
            CheckInEvent.CheckInTipo.RESERVA));
        promListener.onCheckOut(new CheckOutEvent(TENANT, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), 110, 90, new BigDecimal("350.00"),
            LocalDateTime.now()));
        promListener.onReservationCreated(new ReservationCreatedEvent(TENANT, UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
            LocalDateTime.now(), LocalDateTime.now().plusHours(2), false, Instant.now()));
        promListener.onPagamentoConfirmado(new PagamentoConfirmadoEvent(TENANT, UUID.randomUUID(),
            "PIX", new BigDecimal("150.00"), UUID.randomUUID(), Instant.now()));
        promListener.onGruEmitida(new GruEmitidaEvent(TENANT, UUID.randomUUID(),
            UUID.randomUUID(), "PIX", Instant.now()));
        promListener.onCreditoLancado(new CreditoLancadoEvent(TENANT, "ADESAO", 20, 20,
            "Créditos de adesão", null, Instant.now()));

        String scrape = prometheus.scrape();
        assertThat(scrape)
            .contains("jetski_rental_checkin_total{")
            .contains("jetski_rental_checkout_total{")
            .contains("jetski_rental_duration_seconds_sum{")
            .contains("jetski_rental_valor_sum{")
            .contains("jetski_reserva_total{")
            .contains("evento=\"criada\"")
            .contains("jetski_pagamento_valor_sum{")
            .contains("jetski_emissao_total{")
            .contains("jetski_creditos_movimento_total{")
            .contains("tenant_id=\"" + TENANT_TAG + "\"");
    }

    @Test
    @DisplayName("Evento sem tenant não explode — cai na tag 'desconhecido'")
    void testNullTenant() {
        listener.onReservationConfirmed(new ReservationConfirmedEvent(null, UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        assertThat(registry.get("jetski.reserva")
            .tag("tenant_id", "desconhecido").tag("evento", "confirmada")
            .counter().count()).isEqualTo(1.0);
    }

    private double counterValue(String name) {
        return registry.get(name).tag("tenant_id", TENANT_TAG).counter().count();
    }
}
