package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.AceiteRequest;
import com.jetski.locacoes.api.dto.AceiteResponse;
import com.jetski.locacoes.domain.ReservaAceite;
import com.jetski.locacoes.internal.AceiteService;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.UUID;

/**
 * API de aceite/assinatura presencial de uma reserva (balcão).
 * Endpoint sub-recurso: /v1/tenants/{tenantId}/reservas/{id}/aceite
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/reservas/{id}/aceite")
@RequiredArgsConstructor
@Slf4j
public class AceiteController {

    private final AceiteService aceiteService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Registrar aceite/assinatura (balcão)",
        description = "Grava o aceite presencial com evidências (operador, IP, user-agent, hash, origem=BALCAO). " +
                      "Para SIGNATURE_PAD, envie a imagem da assinatura em base64."
    )
    public ResponseEntity<AceiteResponse> registrar(
        @Parameter(description = "UUID do tenant") @PathVariable UUID tenantId,
        @Parameter(description = "UUID da reserva") @PathVariable UUID id,
        @Valid @RequestBody AceiteRequest request,
        HttpServletRequest http
    ) {
        log.info("POST /v1/tenants/{}/reservas/{}/aceite - metodo: {}", tenantId, id, request.getMetodo());
        validateTenantContext(tenantId);

        ReservaAceite.Metodo metodo = parseMetodo(request.getMetodo());
        byte[] assinatura = decodeBase64(request.getAssinaturaBase64());

        ReservaAceite saved = aceiteService.registrar(
            id, metodo, assinatura, http.getRemoteAddr(), http.getHeader("User-Agent"));

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Consultar aceite atual da reserva")
    public ResponseEntity<AceiteResponse> get(
        @PathVariable UUID tenantId,
        @PathVariable UUID id
    ) {
        validateTenantContext(tenantId);
        return aceiteService.getUltimo(id)
            .map(a -> ResponseEntity.ok(toResponse(a)))
            .orElse(ResponseEntity.notFound().build());
    }

    private ReservaAceite.Metodo parseMetodo(String metodo) {
        try {
            return ReservaAceite.Metodo.valueOf(metodo.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException("metodo inválido: " + metodo + " (use SIGNATURE_PAD ou PAPEL)");
        }
    }

    private byte[] decodeBase64(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        String b = base64;
        int comma = b.indexOf(',');
        if (b.startsWith("data:") && comma > 0) {
            b = b.substring(comma + 1); // remove "data:image/png;base64,"
        }
        try {
            return Base64.getDecoder().decode(b.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("assinaturaBase64 inválida");
        }
    }

    private AceiteResponse toResponse(ReservaAceite a) {
        return AceiteResponse.builder()
            .id(a.getId())
            .reservaId(a.getReservaId())
            .operadorId(a.getOperadorId())
            .metodo(a.getMetodo() != null ? a.getMetodo().name() : null)
            .assinaturaS3Key(a.getAssinaturaS3Key())
            .hashSha256(a.getHashSha256())
            .origem(a.getOrigem())
            .aceitoEm(a.getAceitoEm())
            .build();
    }

    private void validateTenantContext(UUID tenantId) {
        UUID contextTenantId = TenantContext.getTenantId();
        if (!tenantId.equals(contextTenantId)) {
            log.error("Tenant mismatch: path={}, context={}", tenantId, contextTenantId);
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
    }
}
