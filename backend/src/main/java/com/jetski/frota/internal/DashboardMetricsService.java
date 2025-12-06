package com.jetski.frota.internal;

import com.jetski.frota.api.dto.DashboardMetrics;
import com.jetski.locacoes.domain.event.RentalCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Service: Dashboard Metrics with Caching
 *
 * Provides cached dashboard revenue metrics using a hybrid approach:
 * - Redis cache for fast reads
 * - Event-driven invalidation on rental completion
 * - TTL fallback (5 minutes) for eventual consistency
 *
 * <p><strong>Cache Strategy:</strong>
 * <ul>
 *   <li>Key format: "dashboard:metrics:{tenantId}:{date}"</li>
 *   <li>Invalidated when RentalCompletedEvent is received</li>
 *   <li>TTL: 5 minutes (safety fallback)</li>
 * </ul>
 *
 * <p><strong>Revenue Calculation:</strong>
 * <ul>
 *   <li>Receita Hoje: Sum of valor_total from rentals checked out TODAY</li>
 *   <li>Receita Mês: Sum of valor_total from rentals checked out in current CALENDAR MONTH (day 1 to today)</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardMetricsService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    private static final String CACHE_KEY_PREFIX = "dashboard:metrics:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /**
     * Get dashboard metrics for a tenant (cached)
     *
     * @param tenantId The tenant UUID
     * @return Dashboard metrics (from cache or freshly calculated)
     */
    @Transactional(readOnly = true)
    public DashboardMetrics getMetrics(UUID tenantId) {
        LocalDate today = LocalDate.now();
        String cacheKey = buildCacheKey(tenantId, today);

        // Try cache first
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof DashboardMetrics metrics) {
                log.debug("Cache HIT for tenant: {} (calculated at: {})", tenantId, metrics.getCalculatedAt());
                return metrics;
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed for key {}: {}", cacheKey, e.getMessage());
            // Continue to calculate from DB
        }

        // Cache miss: calculate from database
        log.debug("Cache MISS for tenant: {} - calculating from database", tenantId);
        DashboardMetrics metrics = calculateFromDatabase(tenantId, today);

        // Store in cache
        try {
            redisTemplate.opsForValue().set(cacheKey, metrics, CACHE_TTL);
            log.debug("Cached metrics for tenant: {} (TTL: {})", tenantId, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis cache write failed for key {}: {}", cacheKey, e.getMessage());
            // Return metrics anyway - cache failure shouldn't break the service
        }

        return metrics;
    }

    /**
     * Handle rental completion event - invalidate cache
     *
     * When a rental is completed (check-out), invalidate the cache for
     * that tenant so the next request gets fresh data.
     *
     * @param event The rental completed event
     */
    @EventListener
    public void onRentalCompleted(RentalCompletedEvent event) {
        log.info("Rental completed event received - invalidating dashboard cache for tenant: {}",
                event.tenantId());

        try {
            // Invalidate cache for today (the rental was just completed)
            String cacheKey = buildCacheKey(event.tenantId(), LocalDate.now());
            Boolean deleted = redisTemplate.delete(cacheKey);

            log.debug("Cache invalidated for key {}: {}", cacheKey, deleted);

            // Also invalidate any keys for this tenant (handles edge case of
            // checkout happening near midnight or cached from previous requests)
            invalidateTenantCache(event.tenantId());

        } catch (Exception e) {
            log.warn("Failed to invalidate cache for tenant {}: {}",
                    event.tenantId(), e.getMessage());
            // Don't throw - cache invalidation failure shouldn't break checkout
        }
    }

    /**
     * Force cache invalidation for a tenant (admin use)
     *
     * @param tenantId The tenant UUID
     */
    public void invalidateTenantCache(UUID tenantId) {
        try {
            String pattern = CACHE_KEY_PREFIX + tenantId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Invalidated {} cache keys for tenant: {}", keys.size(), tenantId);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate tenant cache pattern for {}: {}",
                    tenantId, e.getMessage());
        }
    }

    /**
     * Calculate metrics from database using calendar month
     */
    private DashboardMetrics calculateFromDatabase(UUID tenantId, LocalDate today) {
        LocalDate inicioMes = today.withDayOfMonth(1);

        String sql = """
            SELECT
                COALESCE(SUM(CASE WHEN DATE(data_check_out) = CURRENT_DATE
                    THEN valor_total END), 0) as receita_hoje,
                COALESCE(SUM(valor_total), 0) as receita_mes,
                COUNT(*) FILTER (WHERE DATE(data_check_out) = CURRENT_DATE) as locacoes_hoje,
                COUNT(*) as locacoes_mes
            FROM locacao
            WHERE tenant_id = ?
              AND status = 'FINALIZADA'
              AND data_check_out >= DATE_TRUNC('month', CURRENT_DATE)
              AND data_check_out < DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month'
        """;

        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                DashboardMetrics.builder()
                    .receitaHoje(rs.getBigDecimal("receita_hoje"))
                    .receitaMes(rs.getBigDecimal("receita_mes"))
                    .locacoesHoje(rs.getInt("locacoes_hoje"))
                    .locacoesMes(rs.getInt("locacoes_mes"))
                    .dataReferencia(today)
                    .inicioMes(inicioMes)
                    .calculatedAt(Instant.now())
                    .build(),
                tenantId
            );
        } catch (Exception e) {
            log.error("Failed to calculate metrics for tenant {}: {}", tenantId, e.getMessage());
            return DashboardMetrics.empty(today);
        }
    }

    /**
     * Build cache key
     */
    private String buildCacheKey(UUID tenantId, LocalDate date) {
        return CACHE_KEY_PREFIX + tenantId + ":" + date;
    }
}
