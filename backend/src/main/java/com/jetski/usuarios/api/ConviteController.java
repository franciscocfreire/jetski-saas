package com.jetski.usuarios.api;

import com.jetski.usuarios.api.dto.ConviteSummaryDTO;
import com.jetski.usuarios.internal.ConviteManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller: Invitation Management
 *
 * Endpoints for managing tenant invitations (list, resend, cancel).
 * Available to ADMIN_TENANT and GERENTE roles.
 *
 * @author Jetski Team
 * @since 0.4.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/invitations")
@Tag(name = "Invitations", description = "Gerenciamento de convites pendentes")
@RequiredArgsConstructor
@Slf4j
public class ConviteController {

    private final ConviteManagementService conviteManagementService;

    /**
     * List all pending invitations for a tenant.
     *
     * Returns invitations with status PENDING (including expired ones marked as such).
     *
     * Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @return List of invitation summaries
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Listar convites pendentes",
        description = "Lista todos os convites pendentes do tenant, incluindo os expirados."
    )
    public ResponseEntity<List<ConviteSummaryDTO>> listInvitations(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId
    ) {
        log.info("GET /v1/tenants/{}/invitations", tenantId);

        List<ConviteSummaryDTO> invitations = conviteManagementService.listInvitations(tenantId);

        return ResponseEntity.ok(invitations);
    }

    /**
     * Resend an invitation email.
     *
     * Generates a new token, extends expiration to 48 hours, and sends a new email.
     *
     * Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @param conviteId Invitation UUID (from path)
     * @return Updated invitation summary
     */
    @PostMapping("/{conviteId}/resend")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Reenviar convite",
        description = "Reenvia o email de convite. Gera novo token e estende a validade para mais 48 horas."
    )
    public ResponseEntity<ConviteSummaryDTO> resendInvitation(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do convite")
        @PathVariable UUID conviteId
    ) {
        log.info("POST /v1/tenants/{}/invitations/{}/resend", tenantId, conviteId);

        ConviteSummaryDTO response = conviteManagementService.resendInvitation(tenantId, conviteId);

        return ResponseEntity.ok(response);
    }

    /**
     * Cancel a pending invitation.
     *
     * Marks the invitation as CANCELLED. User will no longer be able to activate.
     *
     * Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @param conviteId Invitation UUID (from path)
     * @return No content on success
     */
    @DeleteMapping("/{conviteId}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Cancelar convite",
        description = "Cancela um convite pendente. O usuário não poderá mais ativar a conta."
    )
    public ResponseEntity<Void> cancelInvitation(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do convite")
        @PathVariable UUID conviteId
    ) {
        log.info("DELETE /v1/tenants/{}/invitations/{}", tenantId, conviteId);

        conviteManagementService.cancelInvitation(tenantId, conviteId);

        return ResponseEntity.noContent().build();
    }
}
