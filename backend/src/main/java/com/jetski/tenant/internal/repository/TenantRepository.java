package com.jetski.tenant.internal.repository;

import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.domain.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository: Tenant
 *
 * Spring Data JPA repository for Tenant entity.
 * Provides CRUD operations and custom queries.
 *
 * @author Jetski Team
 * @since 0.1.0
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /**
     * Find tenant by slug (URL-friendly identifier)
     *
     * @param slug tenant slug (e.g., "acme", "beach-rentals")
     * @return Optional containing tenant if found
     */
    Optional<Tenant> findBySlug(String slug);

    /**
     * Check if slug is already taken
     *
     * @param slug tenant slug
     * @return true if exists
     */
    boolean existsBySlug(String slug);

    /**
     * Find tenant by CNPJ
     *
     * @param cnpj Brazilian company registration number
     * @return Optional containing tenant if found
     */
    Optional<Tenant> findByCnpj(String cnpj);

    /**
     * Check if tenant is active
     *
     * @param id tenant UUID
     * @param status tenant status
     * @return true if tenant exists with given status
     */
    boolean existsByIdAndStatus(UUID id, TenantStatus status);
}
