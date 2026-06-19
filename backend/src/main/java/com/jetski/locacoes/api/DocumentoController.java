package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.DocumentoConsultaResponse;
import com.jetski.locacoes.internal.DocumentoConsultaService;
import com.jetski.locacoes.internal.EmissaoService;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Consulta dos documentos emitidos (PDFs consolidados) das reservas — por
 * cliente ou geral, com URL de download.
 * {@code GET /v1/tenants/{tenantId}/documentos?clienteId=...}
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/documentos")
@Tag(name = "Documentos", description = "Documentos emitidos das reservas")
@RequiredArgsConstructor
@Slf4j
public class DocumentoController {

    private final DocumentoConsultaService service;
    private final EmissaoService emissaoService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR', 'FINANCEIRO')")
    @Operation(summary = "Listar documentos emitidos (filtra por clienteId)")
    public ResponseEntity<List<DocumentoConsultaResponse>> list(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) UUID clienteId
    ) {
        if (!tenantId.equals(TenantContext.getTenantId())) {
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
        return ResponseEntity.ok(service.listar(clienteId));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR', 'FINANCEIRO')")
    @Operation(summary = "Baixar o PDF do documento emitido (streaming)")
    public ResponseEntity<byte[]> download(@PathVariable UUID tenantId, @PathVariable UUID id) {
        if (!tenantId.equals(TenantContext.getTenantId())) {
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
        DocumentoConsultaService.DocumentoArquivo arq = service.baixar(id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + arq.filename() + "\"")
            .body(arq.conteudo());
    }

    @PostMapping("/{id}/reenviar")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR', 'FINANCEIRO')")
    @Operation(summary = "Reenviar por e-mail um documento já emitido (Marinha + cliente)")
    public ResponseEntity<EmissaoService.ResultadoReenvio> reenviar(
        @PathVariable UUID tenantId, @PathVariable UUID id
    ) {
        if (!tenantId.equals(TenantContext.getTenantId())) {
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
        return ResponseEntity.ok(emissaoService.reenviarEmail(id));
    }
}
