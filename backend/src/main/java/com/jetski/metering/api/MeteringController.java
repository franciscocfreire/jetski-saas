package com.jetski.metering.api;

import com.jetski.metering.api.dto.EmissaoMensalDTO;
import com.jetski.metering.internal.MeteringQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Consulta de uso de emissões do tenant (metering — base da cobrança futura).
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/metering")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Metering", description = "Uso de emissões por tenant")
public class MeteringController {

    private final MeteringQueryService meteringQueryService;

    @GetMapping("/emissoes")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Série mensal de emissões (documentos, GRUs e prévias)",
        description = "Contagem por competência dos últimos N meses (máx. 24). Prévias não são cobráveis.")
    public ResponseEntity<List<EmissaoMensalDTO>> emissoes(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "6") int meses) {
        log.info("GET /v1/tenants/{}/metering/emissoes?meses={}", tenantId, meses);
        return ResponseEntity.ok(meteringQueryService.serieMensal(tenantId, meses));
    }
}
