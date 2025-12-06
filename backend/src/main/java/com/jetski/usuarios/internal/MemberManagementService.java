package com.jetski.usuarios.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantContext;
import com.jetski.usuarios.api.dto.DeactivateMemberResponse;
import com.jetski.usuarios.api.dto.ListMembersResponse;
import com.jetski.usuarios.api.dto.MemberSummaryDTO;
import com.jetski.usuarios.domain.Membro;
import com.jetski.usuarios.domain.Usuario;
import com.jetski.usuarios.internal.repository.MembroRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service: Member Management
 *
 * Handles tenant member management:
 * - List members with plan limit info
 * - Deactivate members (soft delete)
 * - Validation (cannot deactivate last admin)
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MemberManagementService {

    private final MembroRepository membroRepository;

    @PersistenceContext
    private final EntityManager entityManager;

    /**
     * List all members of a tenant with plan limit information.
     *
     * @param tenantId Tenant UUID
     * @param includeInactive Include inactive members
     * @return List of members with plan info
     */
    @Transactional(readOnly = true)
    public ListMembersResponse listMembers(UUID tenantId, boolean includeInactive) {
        log.info("Listing members for tenant: {}, includeInactive: {}", tenantId, includeInactive);

        // Query members
        String jpql = """
            SELECT m, u FROM Membro m
            JOIN Usuario u ON m.usuarioId = u.id
            WHERE m.tenantId = :tenantId
            """ + (includeInactive ? "" : " AND m.ativo = true") + """

            ORDER BY m.createdAt DESC
            """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createQuery(jpql)
            .setParameter("tenantId", tenantId)
            .getResultList();

        List<MemberSummaryDTO> members = results.stream()
            .map(row -> {
                Membro m = (Membro) row[0];
                Usuario u = (Usuario) row[1];
                return MemberSummaryDTO.builder()
                    .usuarioId(u.getId())
                    .email(u.getEmail())
                    .nome(u.getNome())
                    .papeis(m.getPapeis())
                    .ativo(Boolean.TRUE.equals(m.getAtivo()))
                    .joinedAt(m.getCreatedAt())
                    .lastUpdated(m.getUpdatedAt())
                    .build();
            })
            .collect(Collectors.toList());

        // Get plan limits and counts
        Integer maxUsuarios = getPlanLimit(tenantId);

        // Count active and inactive members from database (not from filtered list!)
        long activeCount = membroRepository.countByTenantIdAndAtivo(tenantId, true);
        long inactiveCount = membroRepository.countByTenantIdAndAtivo(tenantId, false);

        ListMembersResponse.PlanLimitInfo planLimit = ListMembersResponse.PlanLimitInfo.builder()
            .maxUsuarios(maxUsuarios)
            .currentActive((int) activeCount)
            .available(Math.max(0, maxUsuarios - (int) activeCount))
            .limitReached(activeCount >= maxUsuarios)
            .build();

        return ListMembersResponse.builder()
            .members(members)
            .totalCount(members.size())
            .activeCount((int) activeCount)
            .inactiveCount((int) inactiveCount)
            .planLimit(planLimit)
            .build();
    }

    /**
     * Deactivate a tenant member.
     *
     * Validations:
     * - Member must exist and be active
     * - Cannot deactivate the last ADMIN_TENANT
     *
     * @param tenantId Tenant UUID
     * @param usuarioId User UUID to deactivate
     * @return Deactivation response
     */
    @Transactional
    public DeactivateMemberResponse deactivateMember(UUID tenantId, UUID usuarioId) {
        log.info("Deactivating member: usuario={}, tenant={}", usuarioId, tenantId);

        // Find member
        Membro membro = membroRepository.findByTenantIdAndUsuarioId(tenantId, usuarioId)
            .orElseThrow(() -> new BusinessException(
                "Membro não encontrado neste tenant"
            ));

        if (!Boolean.TRUE.equals(membro.getAtivo())) {
            throw new BusinessException("Membro já está inativo");
        }

        // Check if member is the last active ADMIN_TENANT
        if (hasRole(membro, "ADMIN_TENANT")) {
            long activeAdminCount = membroRepository.countByTenantIdAndPapeisContainingAndAtivo(
                tenantId, "ADMIN_TENANT", true
            );

            if (activeAdminCount <= 1) {
                throw new BusinessException(
                    "Não é possível desativar o último ADMIN_TENANT do tenant"
                );
            }
        }

        // Get usuario for response
        Usuario usuario = entityManager.find(Usuario.class, usuarioId);
        if (usuario == null) {
            throw new BusinessException("Usuário não encontrado");
        }

        // Deactivate member
        membro.setAtivo(false);
        membroRepository.save(membro);

        log.info("Member deactivated successfully: usuario={}, tenant={}", usuarioId, tenantId);

        return DeactivateMemberResponse.success(usuarioId, usuario.getEmail(), tenantId);
    }

    /**
     * Valid roles that can be assigned to members.
     */
    private static final Set<String> VALID_ROLES = Set.of(
        "ADMIN_TENANT", "GERENTE", "OPERADOR", "VENDEDOR", "MECANICO", "FINANCEIRO"
    );

    /**
     * Update member roles.
     *
     * Validations:
     * - All roles must be valid
     * - Cannot remove ADMIN_TENANT if it's the last admin
     *
     * @param tenantId Tenant UUID
     * @param usuarioId User UUID
     * @param papeis New roles to assign
     * @return Updated member summary
     */
    @Transactional
    public MemberSummaryDTO updateRoles(UUID tenantId, UUID usuarioId, List<String> papeis) {
        log.info("Updating roles for member: usuario={}, tenant={}, newRoles={}", usuarioId, tenantId, papeis);

        // Validate roles
        for (String papel : papeis) {
            if (!VALID_ROLES.contains(papel)) {
                throw new BusinessException("Papel inválido: " + papel + ". Papéis válidos: " + VALID_ROLES);
            }
        }

        // Find member
        Membro membro = membroRepository.findByTenantIdAndUsuarioId(tenantId, usuarioId)
            .orElseThrow(() -> new BusinessException("Membro não encontrado neste tenant"));

        if (!Boolean.TRUE.equals(membro.getAtivo())) {
            throw new BusinessException("Não é possível alterar papéis de um membro inativo");
        }

        // Check if removing ADMIN_TENANT from the last admin
        boolean currentlyAdmin = hasRole(membro, "ADMIN_TENANT");
        boolean willBeAdmin = papeis.contains("ADMIN_TENANT");

        if (currentlyAdmin && !willBeAdmin) {
            long activeAdminCount = membroRepository.countByTenantIdAndPapeisContainingAndAtivo(
                tenantId, "ADMIN_TENANT", true
            );

            if (activeAdminCount <= 1) {
                throw new BusinessException(
                    "Não é possível remover o papel ADMIN_TENANT do último administrador do tenant"
                );
            }
        }

        // Update roles
        membro.setPapeis(papeis.toArray(new String[0]));
        membroRepository.save(membro);

        // Get usuario for response
        Usuario usuario = entityManager.find(Usuario.class, usuarioId);

        log.info("Roles updated successfully: usuario={}, tenant={}, newRoles={}", usuarioId, tenantId, papeis);

        return MemberSummaryDTO.builder()
            .usuarioId(usuario.getId())
            .email(usuario.getEmail())
            .nome(usuario.getNome())
            .papeis(membro.getPapeis())
            .ativo(Boolean.TRUE.equals(membro.getAtivo()))
            .joinedAt(membro.getCreatedAt())
            .lastUpdated(membro.getUpdatedAt())
            .build();
    }

    /**
     * Reactivate a deactivated tenant member.
     *
     * Validations:
     * - Member must exist and be inactive
     * - Plan limit must not be exceeded
     *
     * @param tenantId Tenant UUID
     * @param usuarioId User UUID to reactivate
     * @return Reactivated member summary
     */
    @Transactional
    public MemberSummaryDTO reactivateMember(UUID tenantId, UUID usuarioId) {
        log.info("Reactivating member: usuario={}, tenant={}", usuarioId, tenantId);

        // Find member
        Membro membro = membroRepository.findByTenantIdAndUsuarioId(tenantId, usuarioId)
            .orElseThrow(() -> new BusinessException("Membro não encontrado neste tenant"));

        if (Boolean.TRUE.equals(membro.getAtivo())) {
            throw new BusinessException("Membro já está ativo");
        }

        // Check plan limit
        Integer maxUsuarios = getPlanLimit(tenantId);
        long currentActive = membroRepository.countByTenantIdAndAtivo(tenantId, true);

        if (currentActive >= maxUsuarios) {
            throw new BusinessException(
                "Limite de usuários do plano atingido (" + maxUsuarios + "). " +
                "Não é possível reativar este membro."
            );
        }

        // Reactivate member
        membro.setAtivo(true);
        membroRepository.save(membro);

        // Get usuario for response
        Usuario usuario = entityManager.find(Usuario.class, usuarioId);

        log.info("Member reactivated successfully: usuario={}, tenant={}", usuarioId, tenantId);

        return MemberSummaryDTO.builder()
            .usuarioId(usuario.getId())
            .email(usuario.getEmail())
            .nome(usuario.getNome())
            .papeis(membro.getPapeis())
            .ativo(true)
            .joinedAt(membro.getCreatedAt())
            .lastUpdated(membro.getUpdatedAt())
            .build();
    }

    /**
     * Get plan user limit for tenant.
     */
    private Integer getPlanLimit(UUID tenantId) {
        try {
            return (Integer) entityManager.createNativeQuery(
                """
                SELECT (p.limites->>'usuarios_max')::int
                FROM assinatura a
                JOIN plano p ON a.plano_id = p.id
                WHERE a.tenant_id = ?1 AND a.status = 'ativa'
                """
            )
            .setParameter(1, tenantId)
            .getSingleResult();
        } catch (Exception e) {
            log.warn("Failed to get plan limit for tenant {}: {}", tenantId, e.getMessage());
            return 999; // Default fallback
        }
    }

    /**
     * Check if member has a specific role.
     */
    private boolean hasRole(Membro membro, String role) {
        if (membro.getPapeis() == null) {
            return false;
        }
        for (String papel : membro.getPapeis()) {
            if (papel.equals(role)) {
                return true;
            }
        }
        return false;
    }
}
