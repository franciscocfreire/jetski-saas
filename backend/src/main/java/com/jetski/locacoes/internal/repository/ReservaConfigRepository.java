package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.ReservaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository: ReservaConfig
 *
 * Data access for reservation configuration (per-tenant policies).
 *
 * Provides:
 * - Find configuration by tenant ID
 * - Default JPA CRUD operations
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Repository
public interface ReservaConfigRepository extends JpaRepository<ReservaConfig, UUID> {
    // Inherits:
    // - Optional<ReservaConfig> findById(UUID tenantId)
    // - ReservaConfig save(ReservaConfig config)
    // - void delete(ReservaConfig config)
    // - List<ReservaConfig> findAll()
}
