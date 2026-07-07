package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.GruConsultaResponse;
import com.jetski.locacoes.internal.GruConsultaService;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Módulo GRUs: visão do ciclo das GRUs emitidas via EMA — geração, pagamento,
 * emissão, envio à Marinha (V039) e confirmação pela devolutiva (V038).
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/grus")
@RequiredArgsConstructor
@Tag(name = "GRUs", description = "Ciclo das GRUs emitidas (Marinha)")
public class GruConsultaController {

    private final GruConsultaService gruConsultaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR', 'FINANCEIRO')")
    @Operation(summary = "Listar GRUs emitidas com o estado do envio à Marinha")
    public ResponseEntity<List<GruConsultaResponse>> list(@PathVariable UUID tenantId) {
        if (!tenantId.equals(TenantContext.getTenantId())) {
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
        return ResponseEntity.ok(gruConsultaService.listar());
    }
}
