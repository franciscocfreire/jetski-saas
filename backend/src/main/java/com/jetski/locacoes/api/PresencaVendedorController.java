package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.*;
import com.jetski.locacoes.internal.PresencaVendedorService;
import com.jetski.shared.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Seller Daily Attendance (Presença/Diária de Vendedores)
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /presencas/dia - Register daily attendance (batch)</li>
 *   <li>GET /presencas/dia/{data} - Get attendance summary for a date</li>
 *   <li>GET /presencas/vendedores - List active sellers for attendance</li>
 *   <li>GET /presencas/vendedor/{vendedorId} - Get attendance history for a seller</li>
 *   <li>PUT /presencas/{id} - Update a single attendance record</li>
 *   <li>DELETE /presencas/{id} - Delete a single attendance record</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.10.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/presencas")
@RequiredArgsConstructor
@Slf4j
public class PresencaVendedorController {

    private final PresencaVendedorService presencaService;

    /**
     * Register daily attendance for multiple sellers (batch).
     * Permission: GERENTE, ADMIN_TENANT
     */
    @PostMapping("/dia")
    public ResponseEntity<ResumoDiariasResponse> registrarPresencas(
            @PathVariable UUID tenantId,
            @Valid @RequestBody RegistrarPresencasRequest request
    ) {
        UUID registradoPor = TenantContext.getUsuarioId();

        log.info("Registering attendance for date: {} by user: {}", request.getDtReferencia(), registradoPor);

        ResumoDiariasResponse response = presencaService.registrarPresencas(tenantId, request, registradoPor);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get attendance summary for a specific date.
     * Permission: GERENTE, ADMIN_TENANT, FINANCEIRO
     */
    @GetMapping("/dia/{data}")
    public ResponseEntity<ResumoDiariasResponse> getResumoDiarias(
            @PathVariable UUID tenantId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data
    ) {
        log.debug("Getting attendance summary for date: {}", data);

        ResumoDiariasResponse response = presencaService.getResumoDiarias(tenantId, data);

        return ResponseEntity.ok(response);
    }

    /**
     * List active sellers for attendance registration.
     * Permission: GERENTE, ADMIN_TENANT
     */
    @GetMapping("/vendedores")
    public ResponseEntity<List<VendedorResponse>> getVendedoresParaPresenca(
            @PathVariable UUID tenantId
    ) {
        log.debug("Getting active sellers for attendance registration");

        List<VendedorResponse> vendedores = presencaService.getVendedoresParaPresenca();

        return ResponseEntity.ok(vendedores);
    }

    /**
     * Get attendance history for a specific seller.
     * Permission: GERENTE, ADMIN_TENANT, FINANCEIRO
     */
    @GetMapping("/vendedor/{vendedorId}")
    public ResponseEntity<List<PresencaVendedorResponse>> getPresencasByVendedor(
            @PathVariable UUID tenantId,
            @PathVariable UUID vendedorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dtInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dtFim
    ) {
        log.debug("Getting attendance history for seller: {} from {} to {}", vendedorId, dtInicio, dtFim);

        List<PresencaVendedorResponse> presencas = presencaService.getPresencasByVendedor(vendedorId, dtInicio, dtFim);

        return ResponseEntity.ok(presencas);
    }

    /**
     * Update a single attendance record.
     * Permission: GERENTE, ADMIN_TENANT
     */
    @PutMapping("/{presencaId}")
    public ResponseEntity<PresencaVendedorResponse> updatePresenca(
            @PathVariable UUID tenantId,
            @PathVariable UUID presencaId,
            @Valid @RequestBody PresencaVendedorRequest request
    ) {
        log.info("Updating attendance: {}", presencaId);

        PresencaVendedorResponse response = presencaService.updatePresenca(presencaId, request);

        return ResponseEntity.ok(response);
    }

    /**
     * Delete a single attendance record.
     * Permission: GERENTE, ADMIN_TENANT
     */
    @DeleteMapping("/{presencaId}")
    public ResponseEntity<Void> deletePresenca(
            @PathVariable UUID tenantId,
            @PathVariable UUID presencaId
    ) {
        log.info("Deleting attendance: {}", presencaId);

        presencaService.deletePresenca(presencaId);

        return ResponseEntity.noContent().build();
    }
}
