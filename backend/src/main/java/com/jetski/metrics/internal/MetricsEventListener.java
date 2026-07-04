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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Traduz eventos de domínio em métricas Prometheus por tenant.
 *
 * <p>Mesmo contrato do {@code AuditEventListener}: assíncrono e best-effort —
 * falha de métrica nunca quebra o fluxo de negócio. Contadores são incrementos
 * em memória (Micrometer); nada é persistido aqui.
 *
 * <p>Consumo de créditos (CONSUMO) não tem evento próprio — o débito é 1:1 com
 * a emissão de documento, já coberta por {@code jetski.emissao{tipo="DOCUMENTO"}};
 * o saldo por tenant é exposto pelo gauge {@code jetski.creditos.saldo}
 * (BusinessMetricsService).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsEventListener {

    private final BusinessMetrics metrics;

    // ---- Locações -----------------------------------------------------------

    @Async
    @EventListener
    public void onCheckIn(CheckInEvent event) {
        metrics.recordCheckin(tenant(event.tenantId()));
    }

    @Async
    @EventListener
    public void onCheckOut(CheckOutEvent event) {
        String tenant = tenant(event.tenantId());
        metrics.recordCheckout(tenant);
        if (event.minutosUsados() != null) {
            metrics.recordRentalDuration(tenant, event.minutosUsados());
        }
        metrics.recordRentalRevenue(tenant, event.valorTotal());
    }

    // ---- Reservas -----------------------------------------------------------

    @Async
    @EventListener
    public void onReservationCreated(ReservationCreatedEvent event) {
        metrics.recordReservationCreated(tenant(event.tenantId()));
    }

    @Async
    @EventListener
    public void onReservationConfirmed(ReservationConfirmedEvent event) {
        metrics.recordReservationConfirmed(tenant(event.tenantId()));
    }

    @Async
    @EventListener
    public void onReservationCancelled(ReservationCancelledEvent event) {
        metrics.recordReservationCancelled(tenant(event.tenantId()));
    }

    // ---- Pagamentos ---------------------------------------------------------

    @Async
    @EventListener
    public void onPagamentoConfirmado(PagamentoConfirmadoEvent event) {
        metrics.recordPagamentoConfirmado(tenant(event.tenantId()), event.tipo(), event.valorPago());
    }

    @Async
    @EventListener
    public void onPagamentoRecusado(PagamentoRecusadoEvent event) {
        metrics.recordPagamentoRecusado(tenant(event.tenantId()));
    }

    // ---- Emissões (metering) ------------------------------------------------

    @Async
    @EventListener
    public void onDocumentosEmitidos(DocumentosEmitidosEvent event) {
        metrics.recordEmissao(tenant(event.tenantId()), "DOCUMENTO");
    }

    @Async
    @EventListener
    public void onGruEmitida(GruEmitidaEvent event) {
        metrics.recordEmissao(tenant(event.tenantId()), "GRU");
    }

    @Async
    @EventListener
    public void onDocumentoPreviewGerado(DocumentoPreviewGeradoEvent event) {
        metrics.recordEmissao(tenant(event.tenantId()), "PREVIA");
    }

    // ---- Créditos -----------------------------------------------------------

    @Async
    @EventListener
    public void onCreditoLancado(CreditoLancadoEvent event) {
        metrics.recordCreditoMovimento(tenant(event.tenantId()), event.tipo(), event.quantidade());
    }

    private static String tenant(UUID tenantId) {
        return tenantId != null ? tenantId.toString() : "desconhecido";
    }
}
