package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.ReservaConfig;
import com.jetski.locacoes.internal.repository.ReservaConfigRepository;
import com.jetski.shared.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service: ReservaConfig Management
 *
 * Manages per-tenant reservation configuration.
 * Provides default configuration if tenant hasn't configured yet.
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReservaConfigService {

    private final ReservaConfigRepository reservaConfigRepository;

    /**
     * Get configuration for current tenant.
     * Creates default configuration if none exists.
     *
     * @return ReservaConfig for current tenant
     */
    @Transactional
    public ReservaConfig getConfigForCurrentTenant() {
        UUID tenantId = TenantContext.getTenantId();
        return getOrCreateConfig(tenantId);
    }

    /**
     * Get configuration for specific tenant.
     * Creates default configuration if none exists.
     *
     * @param tenantId Tenant UUID
     * @return ReservaConfig for the tenant
     */
    @Transactional
    public ReservaConfig getOrCreateConfig(UUID tenantId) {
        log.debug("Getting reservation config for tenant: {}", tenantId);

        return reservaConfigRepository.findById(tenantId)
            .orElseGet(() -> {
                log.info("Creating default reservation config for tenant: {}", tenantId);
                return createDefaultConfig(tenantId);
            });
    }

    /**
     * Update configuration for current tenant.
     *
     * @param config ReservaConfig to update
     * @return Updated ReservaConfig
     */
    @Transactional
    public ReservaConfig updateConfig(ReservaConfig config) {
        UUID tenantId = TenantContext.getTenantId();

        if (!tenantId.equals(config.getTenantId())) {
            throw new IllegalArgumentException("Tenant ID mismatch");
        }

        config.validate();

        log.info("Updating reservation config for tenant: {}", tenantId);
        return reservaConfigRepository.save(config);
    }

    /**
     * Create default configuration for a tenant.
     *
     * Default values:
     * - Grace period: 30 minutes
     * - Deposit: 30%
     * - Overbooking: 1.5x (50% extra)
     * - Max reservations without deposit: 8
     * - Notifications: enabled, 15 min before expiration
     *
     * @param tenantId Tenant UUID
     * @return Created ReservaConfig with defaults
     */
    private ReservaConfig createDefaultConfig(UUID tenantId) {
        ReservaConfig config = ReservaConfig.builder()
            .tenantId(tenantId)
            .gracePeriodMinutos(30)
            .percentualSinal(new BigDecimal("30.00"))
            .fatorOverbooking(new BigDecimal("1.5"))
            .maxReservasSemSinalPorModelo(8)
            .notificarAntesExpiracao(true)
            .notificarMinutosAntecedencia(15)
            .build();

        return reservaConfigRepository.save(config);
    }
}
