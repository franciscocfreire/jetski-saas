package com.jetski.shared.audit.api;

import com.jetski.shared.audit.api.dto.AuditoriaDTO;
import com.jetski.shared.audit.api.dto.AuditoriaFilters;
import com.jetski.shared.audit.internal.AuditoriaService;
import com.jetski.shared.exception.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * REST Controller for audit log operations.
 *
 * <p>Provides read-only access to audit logs for ADMIN_TENANT and GERENTE roles.
 * Supports filtering by date range, action type, entity, and user.
 *
 * <p><strong>Endpoints:</strong>
 * <ul>
 *   <li>GET /v1/tenants/{tenantId}/auditoria - List with filters</li>
 *   <li>GET /v1/tenants/{tenantId}/auditoria/{id} - Get by ID</li>
 *   <li>GET /v1/tenants/{tenantId}/auditoria/export - Export to CSV</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.10.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/tenants/{tenantId}/auditoria")
@RequiredArgsConstructor
@Tag(name = "Auditoria", description = "Logs de auditoria do sistema")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    /**
     * List audit entries with filters and pagination.
     *
     * @param tenantId Tenant ID
     * @param acao Filter by action (CHECK_IN, CHECK_OUT, etc.)
     * @param entidade Filter by entity type (LOCACAO, JETSKI, etc.)
     * @param entidadeId Filter by specific entity ID
     * @param usuarioId Filter by user ID
     * @param dataInicio Start date (inclusive)
     * @param dataFim End date (inclusive)
     * @param pageable Pagination parameters
     * @return Page of audit entries
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Listar logs de auditoria",
               description = "Retorna logs de auditoria com filtros opcionais")
    public Page<AuditoriaDTO> listar(
            @PathVariable UUID tenantId,
            @Parameter(description = "Filtrar por ação (ex: CHECK_IN, CHECK_OUT)")
            @RequestParam(required = false) String acao,
            @Parameter(description = "Filtrar por tipo de entidade (ex: LOCACAO, JETSKI)")
            @RequestParam(required = false) String entidade,
            @Parameter(description = "Filtrar por ID de entidade específica")
            @RequestParam(required = false) UUID entidadeId,
            @Parameter(description = "Filtrar por ID do usuário")
            @RequestParam(required = false) UUID usuarioId,
            @Parameter(description = "Data início (formato: YYYY-MM-DD)")
            @RequestParam(required = false) LocalDate dataInicio,
            @Parameter(description = "Data fim (formato: YYYY-MM-DD)")
            @RequestParam(required = false) LocalDate dataFim,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        log.debug("GET /auditoria: tenant={}, acao={}, entidade={}, entidadeId={}, periodo={} a {}",
                tenantId, acao, entidade, entidadeId, dataInicio, dataFim);

        AuditoriaFilters filters = new AuditoriaFilters(
                acao, entidade, entidadeId, usuarioId, dataInicio, dataFim
        );

        return auditoriaService.listar(tenantId, filters, pageable);
    }

    /**
     * Get single audit entry by ID.
     *
     * @param tenantId Tenant ID
     * @param id Audit entry ID
     * @return Audit entry details
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Buscar log por ID",
               description = "Retorna detalhes de um log de auditoria específico")
    public AuditoriaDTO buscarPorId(
            @PathVariable UUID tenantId,
            @PathVariable UUID id) {

        log.debug("GET /auditoria/{}: tenant={}", id, tenantId);

        return auditoriaService.buscarPorId(id)
                .orElseThrow(() -> new NotFoundException("Log de auditoria não encontrado: " + id));
    }

    /**
     * Export audit entries to CSV.
     *
     * @param tenantId Tenant ID
     * @param acao Filter by action
     * @param entidade Filter by entity type
     * @param dataInicio Start date
     * @param dataFim End date
     * @return CSV file as byte array
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Exportar logs para CSV",
               description = "Exporta logs de auditoria filtrados em formato CSV")
    public ResponseEntity<byte[]> exportarCsv(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) String acao,
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim) {

        log.info("GET /auditoria/export: tenant={}, acao={}, entidade={}, periodo={} a {}",
                tenantId, acao, entidade, dataInicio, dataFim);

        AuditoriaFilters filters = new AuditoriaFilters(
                acao, entidade, null, null, dataInicio, dataFim
        );

        byte[] csvBytes = auditoriaService.exportarCsv(tenantId, filters);

        String filename = String.format("auditoria_%s.csv",
                LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvBytes);
    }
}
