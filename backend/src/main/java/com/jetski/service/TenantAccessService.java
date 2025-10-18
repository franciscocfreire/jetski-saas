package com.jetski.service;

import com.jetski.domain.entity.Membro;
import com.jetski.domain.entity.UsuarioGlobalRoles;
import com.jetski.domain.repository.MembroRepository;
import com.jetski.domain.repository.UsuarioGlobalRolesRepository;
import com.jetski.service.dto.TenantAccessInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service: TenantAccessService
 *
 * Core service for multi-tenant access control.
 * Validates if a user can access a specific tenant.
 *
 * Access levels:
 * 1. Unrestricted (platform admin) - can access ANY tenant
 * 2. Restricted (normal user) - can only access tenants where they are a member
 *
 * Performance:
 * - Results are cached in Redis for 5 minutes
 * - Database queries use optimized composite indexes
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantAccessService {

    private final MembroRepository membroRepository;
    private final UsuarioGlobalRolesRepository globalRolesRepository;

    /**
     * Validates user access to tenant
     *
     * Algorithm:
     * 1. Check if user has unrestricted platform access (global roles)
     * 2. If not, check if user is a member of the tenant
     * 3. Return access info with roles
     *
     * @param usuarioId User UUID (from JWT sub claim)
     * @param tenantId Tenant UUID (from X-Tenant-Id header)
     * @return TenantAccessInfo with access decision and roles
     */
    @Cacheable(
        value = "tenant-access",
        key = "#usuarioId + ':' + #tenantId",
        unless = "#result == null"
    )
    @Transactional(readOnly = true)
    public TenantAccessInfo validateAccess(UUID usuarioId, UUID tenantId) {
        log.debug("Validating access: user={}, tenant={}", usuarioId, tenantId);

        // 1. Check for unrestricted platform access (super admin)
        Optional<UsuarioGlobalRoles> globalRoles =
            globalRolesRepository.findById(usuarioId);

        if (globalRoles.isPresent() && globalRoles.get().getUnrestrictedAccess()) {
            log.info("Unrestricted access granted: user={}, tenant={}",
                usuarioId, tenantId);
            return TenantAccessInfo.unrestricted(
                Arrays.asList(globalRoles.get().getRoles())
            );
        }

        // 2. Check specific tenant membership
        Optional<Membro> membro = membroRepository
            .findActiveByUsuarioAndTenant(usuarioId, tenantId);

        if (membro.isPresent()) {
            List<String> roles = Arrays.asList(membro.get().getPapeis());
            log.debug("Access granted: user={}, tenant={}, roles={}",
                usuarioId, tenantId, roles);
            return TenantAccessInfo.allowed(roles);
        }

        // 3. Access denied
        log.warn("Access denied: user={}, tenant={}",
            usuarioId, tenantId);
        return TenantAccessInfo.denied("User is not a member of this tenant");
    }

    /**
     * Lists all tenants the user has access to
     *
     * Note: Returns max 100 results for UX.
     * For users with 10k+ tenants, use tenant search API instead.
     *
     * @param usuarioId User UUID
     * @return List of Membro records
     */
    @Transactional(readOnly = true)
    public List<Membro> listUserTenants(UUID usuarioId) {
        return membroRepository.findAllActiveByUsuario(usuarioId);
    }

    /**
     * Counts total tenants user has access to
     *
     * @param usuarioId User UUID
     * @return Count of tenant memberships, or -1 if unrestricted
     */
    @Transactional(readOnly = true)
    public long countUserTenants(UUID usuarioId) {
        // Check if user has unrestricted access
        Optional<UsuarioGlobalRoles> globalRoles =
            globalRolesRepository.findById(usuarioId);

        if (globalRoles.isPresent() && globalRoles.get().getUnrestrictedAccess()) {
            return -1;  // Indicates unrestricted access (infinite tenants)
        }

        // Count specific memberships
        return membroRepository.countActiveByUsuario(usuarioId);
    }
}
