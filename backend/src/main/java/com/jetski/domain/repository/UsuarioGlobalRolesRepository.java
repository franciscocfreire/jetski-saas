package com.jetski.domain.repository;

import com.jetski.domain.entity.UsuarioGlobalRoles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository: UsuarioGlobalRolesRepository
 *
 * Handles database operations for global platform roles.
 *
 * Simple repository - primary key lookups only.
 * Used to check if user is a platform super admin with unrestricted access.
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Repository
public interface UsuarioGlobalRolesRepository extends JpaRepository<UsuarioGlobalRoles, UUID> {
    // No custom methods needed - findById() is sufficient
    // TenantAccessService uses findById(usuarioId) to check unrestricted access
}
