package com.jetski.locacoes.internal.jobs;

import com.jetski.locacoes.internal.ReservaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled Job: Reservation Expiration
 *
 * Automatically expires reservations that have passed their grace period.
 * Runs periodically to mark no-show reservations as EXPIRADA.
 *
 * Business Rules:
 * - Only expires reservations WITHOUT deposit (BAIXA priority)
 * - Reservations WITH deposit (ALTA priority) require manual handling
 * - Checks expiraEm timestamp (calculated as dataInicio + gracePeriodMinutos)
 * - Only processes reservations in PENDENTE or CONFIRMADA status
 *
 * Schedule:
 * - Runs every 5 minutes
 * - Fixed delay ensures job completes before next execution
 *
 * Multi-tenant Handling:
 * - Currently processes all tenants' reservations
 * - TODO: Future enhancement could partition by tenant for scalability
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReservaExpiracaoJob {

    private final ReservaService reservaService;

    /**
     * Execute expiration processing for all tenants.
     * Runs every 5 minutes with fixed delay.
     *
     * Fixed delay = 5min ensures previous execution completes before next starts.
     * This prevents overlapping executions and reduces database contention.
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes = 300,000ms
    public void processExpiredReservations() {
        log.debug("Starting scheduled reservation expiration job");

        try {
            int expiredCount = reservaService.processarExpiracao();

            if (expiredCount > 0) {
                log.info("Reservation expiration job completed: {} reservations expired", expiredCount);
            } else {
                log.debug("Reservation expiration job completed: no reservations to expire");
            }

        } catch (Exception e) {
            log.error("Error during reservation expiration job: {}", e.getMessage(), e);
        }
    }
}
