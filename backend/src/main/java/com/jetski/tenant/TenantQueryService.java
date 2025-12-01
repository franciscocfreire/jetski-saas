package com.jetski.tenant;

import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.internal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Public service for tenant queries.
 *
 * This service exposes read-only tenant operations to other modules,
 * encapsulating the internal TenantRepository.
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantQueryService {

    private final TenantRepository tenantRepository;

    /**
     * Find tenants by their IDs.
     *
     * @param tenantIds List of tenant IDs to find
     * @return Map of tenant ID to Tenant entity
     */
    public Map<UUID, Tenant> findTenantsById(List<UUID> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return Map.of();
        }
        return tenantRepository.findAllById(tenantIds).stream()
            .collect(Collectors.toMap(Tenant::getId, t -> t));
    }

    /**
     * Find a tenant by ID.
     *
     * @param tenantId Tenant ID
     * @return Tenant if found, null otherwise
     */
    public Tenant findById(UUID tenantId) {
        return tenantRepository.findById(tenantId).orElse(null);
    }
}
