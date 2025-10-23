package com.jetski.usuarios.api;

import com.jetski.usuarios.api.dto.DeactivateMemberResponse;
import com.jetski.usuarios.api.dto.ListMembersResponse;
import com.jetski.usuarios.internal.MemberManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller: Tenant Member Management
 *
 * Endpoints for managing tenant members (list, deactivate).
 * Available to ADMIN_TENANT and GERENTE roles.
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/members")
@Tag(name = "Tenant Members", description = "Gerenciamento de membros do tenant")
@RequiredArgsConstructor
@Slf4j
public class TenantMemberController {

    private final MemberManagementService memberManagementService;

    /**
     * List all members of a tenant.
     *
     * Returns member list with plan limit information.
     * Query parameter 'includeInactive' defaults to false.
     *
     * Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @param includeInactive Include inactive members (default: false)
     * @return List of members with plan limit info
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Listar membros do tenant",
        description = "Lista todos os membros do tenant com informações de limite do plano. " +
                      "Por padrão, retorna apenas membros ativos. Use ?includeInactive=true para incluir inativos."
    )
    public ResponseEntity<ListMembersResponse> listMembers(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "Incluir membros inativos")
        @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        log.info("GET /v1/tenants/{}/members?includeInactive={}", tenantId, includeInactive);

        ListMembersResponse response = memberManagementService.listMembers(tenantId, includeInactive);

        return ResponseEntity.ok(response);
    }

    /**
     * Deactivate a tenant member.
     *
     * Soft-delete: sets membro.ativo = false.
     *
     * Validations:
     * - Member must exist and be active
     * - Cannot deactivate the last ADMIN_TENANT
     *
     * Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @param usuarioId User UUID to deactivate (from path)
     * @return Deactivation response
     */
    @DeleteMapping("/{usuarioId}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Desativar membro do tenant",
        description = "Desativa um membro do tenant (soft delete). " +
                      "Não é possível desativar o último ADMIN_TENANT."
    )
    public ResponseEntity<DeactivateMemberResponse> deactivateMember(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do usuário a desativar")
        @PathVariable UUID usuarioId
    ) {
        log.info("DELETE /v1/tenants/{}/members/{}", tenantId, usuarioId);

        DeactivateMemberResponse response = memberManagementService.deactivateMember(tenantId, usuarioId);

        return ResponseEntity.ok(response);
    }
}
