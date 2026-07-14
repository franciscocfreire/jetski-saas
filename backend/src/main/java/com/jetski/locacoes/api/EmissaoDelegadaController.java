package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.EmissaoDelegadaResponse;
import com.jetski.locacoes.internal.EmissaoDelegadaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Painel da EAMA emissora: emissões feitas em seu nome (EMISSAO_DELEGADA_SPEC
 * §4.4, V048). Rego: {@code emissao-delegada:*} para GERENTE (+ wildcard do
 * ADMIN_TENANT).
 *
 * @author Jetski Team
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/emissoes-delegadas")
@RequiredArgsConstructor
@Tag(name = "Emissões delegadas", description = "Painel da EAMA emissora")
public class EmissaoDelegadaController {

    private final EmissaoDelegadaService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Emissões feitas em nome desta EAMA (filtro opcional por operadora)")
    public List<EmissaoDelegadaResponse> listar(
            @PathVariable UUID tenantId,
            @RequestParam(value = "operadoraId", required = false) UUID operadoraId,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return service.listar(tenantId, operadoraId, limit).stream()
            .map(EmissaoDelegadaResponse::of)
            .toList();
    }

    @GetMapping("/contagens")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Contagem mensal por operadora (base do acerto financeiro por fora)")
    public List<Map<String, Object>> contagens(@PathVariable UUID tenantId) {
        return service.contagens(tenantId).stream()
            .map(r -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("operadoraTenantId", r[0]);
                m.put("operadoraNome", r[1]);
                m.put("mes", r[2]);
                m.put("total", r[3]);
                return m;
            })
            .toList();
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "URL presignada do PDF espelhado")
    public Map<String, String> download(@PathVariable UUID tenantId, @PathVariable("id") UUID id) {
        return Map.of("url", service.downloadUrl(tenantId, id));
    }

    @PostMapping("/{id}/reenviar")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Reenvia o PDF já emitido à Capitania (ou destinatário pontual)",
        description = "Não re-emite e não debita crédito; o rastro do reenvio fica no espelho."
    )
    public EmissaoDelegadaResponse reenviar(
            @PathVariable UUID tenantId,
            @PathVariable("id") UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String destino = body != null ? body.get("destino") : null;
        return EmissaoDelegadaResponse.of(service.reenviar(tenantId, id, destino));
    }
}
