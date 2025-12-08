package com.jetski.shared.audit.internal;

import com.jetski.locacoes.domain.event.CheckInEvent;
import com.jetski.locacoes.domain.event.CheckOutEvent;
import com.jetski.shared.audit.domain.Auditoria;
import com.jetski.shared.audit.domain.AuditoriaRepository;
import com.jetski.shared.observability.MDCKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized Event Listener for Audit Logging.
 *
 * <p>Listens to domain events and persists audit entries to the database.
 * This component enables loose coupling between business operations
 * and audit requirements.
 *
 * <p><strong>Features:</strong>
 * <ul>
 *   <li>Async processing with TenantContext propagation (via AsyncConfig TaskDecorator)</li>
 *   <li>REQUIRES_NEW transaction for isolation from main business transaction</li>
 *   <li>Automatic context capture (trace_id, IP, user agent)</li>
 *   <li>JSONB snapshots for before/after state</li>
 *   <li>Graceful error handling (audit failures don't break business flow)</li>
 * </ul>
 *
 * <p><strong>Supported Events:</strong>
 * <ul>
 *   <li>{@link CheckInEvent} - When a rental starts</li>
 *   <li>{@link CheckOutEvent} - When a rental completes</li>
 * </ul>
 *
 * <p><strong>Future Events (add listeners as needed):</strong>
 * <ul>
 *   <li>JetskiStatusChangedEvent</li>
 *   <li>MaintenanceOrderEvent</li>
 *   <li>ReservationCreatedEvent</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.10.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditoriaRepository auditoriaRepository;

    /**
     * Handles check-in events.
     *
     * <p>Captures the initial state when a rental begins, including:
     * <ul>
     *   <li>Jetski ID and initial hourmeter</li>
     *   <li>Customer and operator info</li>
     *   <li>Check-in type (reservation vs walk-in)</li>
     * </ul>
     *
     * @param event the check-in domain event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCheckIn(CheckInEvent event) {
        try {
            log.debug("Processing audit for check-in event: locacaoId={}", event.locacaoId());

            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("locacaoId", event.locacaoId().toString());
            dadosNovos.put("jetskiId", event.jetskiId().toString());
            dadosNovos.put("horimetroInicio", event.horimetroInicio());
            dadosNovos.put("dataCheckIn", event.dataCheckIn().toString());
            dadosNovos.put("tipo", event.tipo().name());

            if (event.reservaId() != null) {
                dadosNovos.put("reservaId", event.reservaId().toString());
            }
            if (event.clienteId() != null) {
                dadosNovos.put("clienteId", event.clienteId().toString());
            }

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.operadorId())
                    .acao("CHECK_IN")
                    .entidade("LOCACAO")
                    .entidadeId(event.locacaoId())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();

            auditoriaRepository.save(auditoria);
            log.info("Audit entry created for check-in: locacaoId={}, auditId={}",
                    event.locacaoId(), auditoria.getId());

        } catch (Exception e) {
            // Log but don't throw - audit failures should not break business flow
            log.error("Failed to create audit entry for check-in: locacaoId={}, error={}",
                    event.locacaoId(), e.getMessage(), e);
        }
    }

    /**
     * Handles check-out events.
     *
     * <p>Captures the final state when a rental completes, including:
     * <ul>
     *   <li>Final hourmeter and minutes used</li>
     *   <li>Total value charged</li>
     *   <li>Check-out timestamp</li>
     * </ul>
     *
     * @param event the check-out domain event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCheckOut(CheckOutEvent event) {
        try {
            log.debug("Processing audit for check-out event: locacaoId={}", event.locacaoId());

            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("locacaoId", event.locacaoId().toString());
            dadosNovos.put("jetskiId", event.jetskiId().toString());
            dadosNovos.put("horimetroFim", event.horimetroFim());
            dadosNovos.put("minutosUsados", event.minutosUsados());
            dadosNovos.put("valorTotal", event.valorTotal() != null ? event.valorTotal().toString() : null);
            dadosNovos.put("dataCheckOut", event.dataCheckOut().toString());

            if (event.clienteId() != null) {
                dadosNovos.put("clienteId", event.clienteId().toString());
            }

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.operadorId())
                    .acao("CHECK_OUT")
                    .entidade("LOCACAO")
                    .entidadeId(event.locacaoId())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();

            auditoriaRepository.save(auditoria);
            log.info("Audit entry created for check-out: locacaoId={}, auditId={}, valorTotal={}",
                    event.locacaoId(), auditoria.getId(), event.valorTotal());

        } catch (Exception e) {
            // Log but don't throw - audit failures should not break business flow
            log.error("Failed to create audit entry for check-out: locacaoId={}, error={}",
                    event.locacaoId(), e.getMessage(), e);
        }
    }

    // ===================================================================
    // Context Capture Helpers
    // ===================================================================

    /**
     * Gets the trace ID from MDC (set by RequestCorrelationFilter).
     */
    private String getTraceId() {
        return MDC.get(MDCKeys.TRACE_ID);
    }

    /**
     * Gets the remote IP from MDC (set by RequestCorrelationFilter).
     */
    private String getRemoteIp() {
        return MDC.get(MDCKeys.REMOTE_IP);
    }
}
