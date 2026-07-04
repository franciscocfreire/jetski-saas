package com.jetski.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Business Metrics - Custom metrics for Jetski SaaS application
 *
 * This class provides custom business metrics for monitoring:
 * - Rental operations (check-in, check-out, duration, revenue)
 * - Reservation operations (created, confirmed, cancelled)
 * - Payments, document emissions (metering) and emission credits
 * - Authentication operations (login, token refresh)
 * - Multi-tenant operations (tenant context switches)
 *
 * All metrics are tagged with tenant_id for per-tenant monitoring. Meters are
 * registered lazily on first record — never pre-register a meter name here with
 * a different tag set (Prometheus rejects the same name with different tag keys,
 * which silently drops the metric).
 *
 * @author Jetski Team
 * @since 0.7.5
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // ========================================
    // Rental Metrics
    // ========================================

    /**
     * Record a check-in operation
     * @param tenantId Tenant identifier
     */
    public void recordCheckin(String tenantId) {
        Counter.builder("jetski.rental.checkin")
                .tag("tenant_id", tenantId)
                .register(registry)
                .increment();
    }

    /**
     * Record a check-out operation
     * @param tenantId Tenant identifier
     */
    public void recordCheckout(String tenantId) {
        Counter.builder("jetski.rental.checkout")
                .tag("tenant_id", tenantId)
                .register(registry)
                .increment();
    }

    /**
     * Record rental duration
     * @param tenantId Tenant identifier
     * @param durationMinutes Duration in minutes
     */
    public void recordRentalDuration(String tenantId, long durationMinutes) {
        Timer.builder("jetski.rental.duration")
                .tag("tenant_id", tenantId)
                .register(registry)
                .record(durationMinutes, TimeUnit.MINUTES);
    }

    // ========================================
    // Reservation Metrics
    // ========================================

    /**
     * Record a reservation creation
     * @param tenantId Tenant identifier
     */
    public void recordReservationCreated(String tenantId) {
        reservaEvento(tenantId, "criada");
    }

    /**
     * Record a reservation confirmation
     * @param tenantId Tenant identifier
     */
    public void recordReservationConfirmed(String tenantId) {
        reservaEvento(tenantId, "confirmada");
    }

    /**
     * Record a reservation cancellation. The free-text cancellation reason is
     * deliberately NOT a tag (unbounded label cardinality).
     * @param tenantId Tenant identifier
     */
    public void recordReservationCancelled(String tenantId) {
        reservaEvento(tenantId, "cancelada");
    }

    /**
     * Single counter family jetski_reserva_total{evento=...}. A name ending in a
     * Prometheus reserved suffix (.created, .count, .sum, .total...) would be
     * silently stripped by the Prometheus 1.x client — hence the evento tag
     * instead of one metric per lifecycle step.
     */
    private void reservaEvento(String tenantId, String evento) {
        Counter.builder("jetski.reserva")
                .description("Eventos do ciclo de vida de reservas")
                .tag("tenant_id", tenantId)
                .tag("evento", evento)
                .register(registry)
                .increment();
    }

    // ========================================
    // Revenue & Payment Metrics
    // ========================================

    /**
     * Record the total value of a completed rental (check-out).
     * Exposed as jetski_rental_valor_sum/_count (sum = revenue in BRL).
     * @param tenantId Tenant identifier
     * @param valorTotal Total charged for the rental
     */
    public void recordRentalRevenue(String tenantId, BigDecimal valorTotal) {
        if (valorTotal == null) {
            return;
        }
        DistributionSummary.builder("jetski.rental.valor")
                .description("Valor total das locações finalizadas (BRL)")
                .tag("tenant_id", tenantId)
                .register(registry)
                .record(valorTotal.doubleValue());
    }

    /**
     * Record a confirmed reservation payment.
     * @param tenantId Tenant identifier
     * @param tipo Payment type (e.g. PIX, SINAL) — bounded enum values only
     * @param valorPago Amount paid (nullable)
     */
    public void recordPagamentoConfirmado(String tenantId, String tipo, BigDecimal valorPago) {
        DistributionSummary.builder("jetski.pagamento.valor")
                .description("Pagamentos de reserva confirmados (BRL)")
                .tag("tenant_id", tenantId)
                .tag("tipo", tipo != null ? tipo : "desconhecido")
                .register(registry)
                .record(valorPago != null ? valorPago.doubleValue() : 0.0);
    }

    /**
     * Record a refused reservation payment.
     * @param tenantId Tenant identifier
     */
    public void recordPagamentoRecusado(String tenantId) {
        Counter.builder("jetski.pagamento.recusado")
                .description("Pagamentos de reserva recusados")
                .tag("tenant_id", tenantId)
                .register(registry)
                .increment();
    }

    // ========================================
    // Emission (Metering) & Credit Metrics
    // ========================================

    /**
     * Record a billable emission (metering fact).
     * @param tenantId Tenant identifier
     * @param tipo Emission type: DOCUMENTO, GRU or PREVIA
     */
    public void recordEmissao(String tenantId, String tipo) {
        Counter.builder("jetski.emissao")
                .description("Emissões contabilizadas (metering)")
                .tag("tenant_id", tenantId)
                .tag("tipo", tipo)
                .register(registry)
                .increment();
    }

    /**
     * Record an emission-credit ledger entry (grant or manual adjustment).
     * @param tenantId Tenant identifier
     * @param tipo Ledger entry type (ADESAO, AJUSTE, ...) — bounded enum values only
     * @param quantidade Signed quantity; recorded as absolute amount under the tipo tag
     */
    public void recordCreditoMovimento(String tenantId, String tipo, int quantidade) {
        Counter.builder("jetski.creditos.movimento")
                .description("Movimentações de créditos de emissão")
                .tag("tenant_id", tenantId)
                .tag("tipo", tipo)
                .register(registry)
                .increment(Math.abs(quantidade));
    }

    // ========================================
    // Authentication Metrics
    // ========================================

    /**
     * Record a successful login
     * @param tenantId Tenant identifier (optional, can be null for platform admins)
     */
    public void recordLoginSuccess(String tenantId) {
        Counter.builder("jetski.auth.login.success")
                .tag("tenant_id", tenantId != null ? tenantId : "platform")
                .register(registry)
                .increment();
    }

    /**
     * Record a failed login attempt
     * @param reason Failure reason (invalid_credentials, account_locked, etc.)
     */
    public void recordLoginFailure(String reason) {
        Counter.builder("jetski.auth.login.failure")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    /**
     * Record a token refresh operation
     * @param tenantId Tenant identifier
     */
    public void recordTokenRefresh(String tenantId) {
        Counter.builder("jetski.auth.token.refresh")
                .tag("tenant_id", tenantId != null ? tenantId : "platform")
                .register(registry)
                .increment();
    }

    // ========================================
    // Tenant Metrics
    // ========================================

    /**
     * Record a tenant context switch
     * @param tenantId Tenant identifier
     */
    public void recordTenantContextSwitch(String tenantId) {
        Counter.builder("jetski.tenant.context.switch")
                .tag("tenant_id", tenantId)
                .register(registry)
                .increment();
    }

    // ========================================
    // OPA Authorization Metrics
    // ========================================

    /**
     * Record an OPA authorization decision
     * @param tenantId Tenant identifier
     * @param decision Decision result (allow, deny)
     * @param policy Policy name
     */
    public void recordOpaDecision(String tenantId, String decision, String policy) {
        Counter.builder("jetski.opa.decision")
                .tag("tenant_id", tenantId)
                .tag("decision", decision)
                .tag("policy", policy)
                .register(registry)
                .increment();
    }

    /**
     * Record OPA decision duration
     * @param tenantId Tenant identifier
     * @param durationMs Duration in milliseconds
     */
    public void recordOpaDecisionDuration(String tenantId, long durationMs) {
        Timer.builder("jetski.opa.decision.duration")
                .tag("tenant_id", tenantId)
                .register(registry)
                .record(Duration.ofMillis(durationMs));
    }

    /**
     * Get the meter registry for advanced metrics operations
     */
    public MeterRegistry getRegistry() {
        return registry;
    }
}
