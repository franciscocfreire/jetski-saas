package com.jetski.locacoes.api;

import com.jetski.locacoes.internal.EmissaoService;
import com.jetski.locacoes.internal.PdfLinkService;
import com.jetski.shared.security.TenantContext;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * API de emissão dos documentos consolidados de uma reserva (balcão).
 * POST /v1/tenants/{tenantId}/reservas/{id}/emitir-documentos
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/reservas/{id}/emitir-documentos")
@RequiredArgsConstructor
@Slf4j
public class EmissaoController {

    private final EmissaoService emissaoService;
    private final PdfLinkService pdfLinkService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Emitir documentos consolidados",
        description = "Gera o PDF (anexos + termo), arquiva, registra, envia à Marinha e ao cliente, " +
                      "e devolve a URL de download + dados da GRU. Exige habilitação resolvida e termos assinados."
    )
    public ResponseEntity<EmissaoService.ResultadoEmissao> emitir(
        @Parameter(description = "UUID do tenant") @PathVariable UUID tenantId,
        @Parameter(description = "UUID da reserva") @PathVariable UUID id
    ) {
        log.info("POST /v1/tenants/{}/reservas/{}/emitir-documentos", tenantId, id);
        validateTenantContext(tenantId);
        return ResponseEntity.ok(emissaoService.emitir(id));
    }

    @GetMapping(value = "/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Pré-visualizar o documento (sem enviar)",
        description = "Gera o PDF que o destino (MARINHA|CLIENTE) receberá, respeitando a parametrização "
                    + "do tenant. Não envia e-mail, não persiste e não altera o status. Carimba RASCUNHO "
                    + "enquanto houver pendências."
    )
    public ResponseEntity<byte[]> preview(
        @Parameter(description = "UUID do tenant") @PathVariable UUID tenantId,
        @Parameter(description = "UUID da reserva") @PathVariable UUID id,
        @Parameter(description = "Destino: MARINHA ou CLIENTE")
        @RequestParam(defaultValue = "CLIENTE") EmissaoService.Destino destino
    ) {
        validateTenantContext(tenantId);
        byte[] pdf = emissaoService.preview(id, destino);
        String nome = "previa-" + destino.name().toLowerCase() + ".pdf";
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nome + "\"")
            .body(pdf);
    }

    @GetMapping("/preview-link")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Link temporário da prévia (abre por URL, compatível com iOS)",
        description = "Gera a prévia e devolve uma URL pública de uso único (/v1/pdf/<token>) "
                    + "para abrir o PDF — o iOS Safari não renderiza blob: em aba nova."
    )
    public ResponseEntity<Map<String, String>> previewLink(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @RequestParam(defaultValue = "CLIENTE") EmissaoService.Destino destino
    ) {
        validateTenantContext(tenantId);
        String url = pdfLinkService.criarLink(emissaoService.preview(id, destino));
        return ResponseEntity.ok(Map.of("url", url));
    }

    private void validateTenantContext(UUID tenantId) {
        UUID contextTenantId = TenantContext.getTenantId();
        if (!tenantId.equals(contextTenantId)) {
            log.error("Tenant mismatch: path={}, context={}", tenantId, contextTenantId);
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
    }
}
