package com.jetski.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Business Metrics - Custom metrics for Jetski SaaS application
 *
 * This class provides custom business metrics for monitoring:
 * - Rental operations (check-in, check-out)
 * - Reservation operations (created, confirmed, cancelled)
 * - Authentication operations (login, token refresh)
 * - Multi-tenant operations (tenant context switches)
 *
 * All metrics are tagged with tenant_id for per-tenant monitoring.
 *
 * @author Jetski Team
 * @since 0.7.5
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    // Rental metrics
    private final Counter checkinCounter;
    private final Counter checkoutCounter;
    private final Timer rentalDurationTimer;

    // Reservation metrics
    private final Counter reservationCreatedCounter;
    private final Counter reservationConfirmedCounter;
    private final Counter reservationCancelledCounter;

    // Authentication metrics
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter tokenRefreshCounter;

    // Tenant context metrics
    private final Counter tenantContextSwitchCounter;

    // OPA authorization metrics
    private final Counter opaDecisionCounter;
    private final Timer opaDecisionTimer;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;

        // Initialize rental metrics
        this.checkinCounter = Counter.builder("jetski.rental.checkin")
                .description("Number of rental check-ins performed")
                .tag("type", "business")
                .register(registry);

        this.checkoutCounter = Counter.builder("jetski.rental.checkout")
                .description("Number of rental check-outs performed")
                .tag("type", "business")
                .register(registry);

        this.rentalDurationTimer = Timer.builder("jetski.rental.duration")
                .description("Duration of rentals (in minutes)")
                .tag("type", "business")
                .register(registry);

        // Initialize reservation metrics
        this.reservationCreatedCounter = Counter.builder("jetski.reservation.created")
                .description("Number of reservations created")
                .tag("type", "business")
                .register(registry);

        this.reservationConfirmedCounter = Counter.builder("jetski.reservation.confirmed")
                .description("Number of reservations confirmed")
                .tag("type", "business")
                .register(registry);

        this.reservationCancelledCounter = Counter.builder("jetski.reservation.cancelled")
                .description("Number of reservations cancelled")
                .tag("type", "business")
                .register(registry);

        // Initialize authentication metrics
        this.loginSuccessCounter = Counter.builder("jetski.auth.login.success")
                .description("Number of successful logins")
                .tag("type", "security")
                .register(registry);

        this.loginFailureCounter = Counter.builder("jetski.auth.login.failure")
                .description("Number of failed login attempts")
                .tag("type", "security")
                .register(registry);

        this.tokenRefreshCounter = Counter.builder("jetski.auth.token.refresh")
                .description("Number of token refresh operations")
                .tag("type", "security")
                .register(registry);

        // Initialize tenant metrics
        this.tenantContextSwitchCounter = Counter.builder("jetski.tenant.context.switch")
                .description("Number of tenant context switches")
                .tag("type", "multi-tenant")
                .register(registry);

        // Initialize OPA metrics
        this.opaDecisionCounter = Counter.builder("jetski.opa.decision")
                .description("Number of OPA authorization decisions")
                .tag("type", "security")
                .register(registry);

        this.opaDecisionTimer = Timer.builder("jetski.opa.decision.duration")
                .description("Duration of OPA authorization decisions")
                .tag("type", "security")
                .register(registry);
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
        Counter.builder("jetski.reservation.created")
                .tag("tenant_id", tenantId)
                .register(registry)
                .increment();
    }

    /**
     * Record a reservation confirmation
     * @param tenantId Tenant identifier
     */
    public void recordReservationConfirmed(String tenantId) {
        Counter.builder("jetski.reservation.confirmed")
                .tag("tenant_id", tenantId)
                .register(registry)
                .increment();
    }

    /**
     * Record a reservation cancellation
     * @param tenantId Tenant identifier
     * @param reason Cancellation reason
     */
    public void recordReservationCancelled(String tenantId, String reason) {
        Counter.builder("jetski.reservation.cancelled")
                .tag("tenant_id", tenantId)
                .tag("reason", reason)
                .register(registry)
                .increment();
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
