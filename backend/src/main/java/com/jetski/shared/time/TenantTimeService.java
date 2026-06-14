package com.jetski.shared.time;

import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Service for tenant-aware time operations.
 *
 * Provides current date/time based on the tenant's configured timezone,
 * rather than the server's default timezone.
 *
 * This is crucial for multi-tenant SaaS where tenants can be in different
 * timezones (e.g., America/Sao_Paulo, America/Fortaleza, etc.).
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantTimeService {

    private static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";

    private final TenantQueryService tenantQueryService;

    /**
     * Get the current LocalDateTime in the tenant's timezone.
     *
     * Uses the tenant from TenantContext to determine the timezone.
     * Falls back to America/Sao_Paulo if tenant or timezone is not found.
     *
     * @return current LocalDateTime in tenant's timezone
     */
    public LocalDateTime now() {
        ZoneId zoneId = getTenantZoneId();
        return ZonedDateTime.now(zoneId).toLocalDateTime();
    }

    /**
     * Get the current LocalDate in the tenant's timezone.
     *
     * @return current LocalDate in tenant's timezone
     */
    public LocalDate today() {
        ZoneId zoneId = getTenantZoneId();
        return ZonedDateTime.now(zoneId).toLocalDate();
    }

    /**
     * Get the current LocalDateTime for a specific tenant.
     *
     * @param tenantId the tenant ID
     * @return current LocalDateTime in the tenant's timezone
     */
    public LocalDateTime now(UUID tenantId) {
        ZoneId zoneId = getZoneIdForTenant(tenantId);
        return ZonedDateTime.now(zoneId).toLocalDateTime();
    }

    /**
     * Get the current LocalDate for a specific tenant.
     *
     * @param tenantId the tenant ID
     * @return current LocalDate in the tenant's timezone
     */
    public LocalDate today(UUID tenantId) {
        ZoneId zoneId = getZoneIdForTenant(tenantId);
        return ZonedDateTime.now(zoneId).toLocalDate();
    }

    /**
     * Get the ZoneId for the current tenant from TenantContext.
     *
     * @return ZoneId for the current tenant, or default if not found
     */
    public ZoneId getTenantZoneId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            log.warn("No tenant in context, using default timezone: {}", DEFAULT_TIMEZONE);
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
        return getZoneIdForTenant(tenantId);
    }

    /**
     * Get the ZoneId for a specific tenant.
     *
     * @param tenantId the tenant ID
     * @return ZoneId for the tenant, or default if not found
     */
    public ZoneId getZoneIdForTenant(UUID tenantId) {
        try {
            Tenant tenant = tenantQueryService.findById(tenantId);
            if (tenant != null && tenant.getTimezone() != null) {
                return ZoneId.of(tenant.getTimezone());
            }
        } catch (Exception e) {
            log.warn("Error getting timezone for tenant {}: {}", tenantId, e.getMessage());
        }
        log.debug("Using default timezone for tenant {}: {}", tenantId, DEFAULT_TIMEZONE);
        return ZoneId.of(DEFAULT_TIMEZONE);
    }

    /**
     * Convert a LocalDateTime from UTC to tenant's timezone.
     *
     * @param utcDateTime the UTC LocalDateTime
     * @return LocalDateTime in tenant's timezone
     */
    public LocalDateTime fromUtcToTenantTime(LocalDateTime utcDateTime) {
        if (utcDateTime == null) return null;
        ZoneId tenantZone = getTenantZoneId();
        return utcDateTime.atZone(ZoneId.of("UTC"))
                .withZoneSameInstant(tenantZone)
                .toLocalDateTime();
    }

    /**
     * Convert a LocalDateTime from tenant's timezone to UTC.
     *
     * @param tenantDateTime the LocalDateTime in tenant's timezone
     * @return LocalDateTime in UTC
     */
    public LocalDateTime fromTenantTimeToUtc(LocalDateTime tenantDateTime) {
        if (tenantDateTime == null) return null;
        ZoneId tenantZone = getTenantZoneId();
        return tenantDateTime.atZone(tenantZone)
                .withZoneSameInstant(ZoneId.of("UTC"))
                .toLocalDateTime();
    }
}
