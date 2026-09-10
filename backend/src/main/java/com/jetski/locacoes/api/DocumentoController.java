package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.DocumentoConsultaResponse;
import com.jetski.locacoes.api.dto.DocumentoEnvioStatusResponse;
import com.jetski.locacoes.internal.DocumentoEnvioService;
import com.jetski.locacoes.internal.DocumentoConsultaService;
import com.jetski.locacoes.internal.EmissaoService;
import com.jetski.locacoes.internal.PdfLinkService;
import com.jetski.shared.security.TenantContext;

import java.util.Map;
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
    private final PdfLinkService pdfLinkService;
    private final DocumentoEnvioService documentoEnvioService;

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
        // filename* em UTF-8 (RFC 5987): o nome tem espaços e acentos ("Fulano de Tal 123.pdf");
        // concatenado à mão no header, o navegador trunca no primeiro espaço.
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition
                .inline().filename(arq.filename(), java.nio.charset.StandardCharsets.UTF_8)
                .build().toString())
            .body(arq.conteudo());
    }

    @GetMapping("/{id}/download-link")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR', 'FINANCEIRO')")
    @Operation(
        summary = "Link do documento emitido (abre por URL, compatível com iOS)",
        description = "Link compartilhável: multiuso e válido por dias (jetski.pdf-link.compartilhavel-dias), "
                    + "para o operador enviar ao cliente. Abre sem login — quem tem a URL vê o PDF."
    )
    public ResponseEntity<Map<String, String>> downloadLink(
        @PathVariable UUID tenantId, @PathVariable UUID id
    ) {
        if (!tenantId.equals(TenantContext.getTenantId())) {
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
        // Link de COMPARTILHAMENTO: guarda a referência ao objeto no storage (não os
        // bytes), vale dias e é multiuso — o operador manda ao cliente por e-mail/WhatsApp.
        DocumentoConsultaService.Referencia ref = service.referencia(id);
        String url = pdfLinkService.criarLinkCompartilhavel(ref.s3Key(), ref.filename());
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/{id}/envio")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR', 'FINANCEIRO')")
    @Operation(
        summary = "Estado do envio por e-mail do documento",
        description = "Consultado em polling pela tela de emissão enquanto os e-mails saem "
                    + "fora do request. Estritamente read-only: NÃO retenta o envio — senão um "
                    + "F5 do operador viraria um loop de e-mails à Capitania."
    )
    public ResponseEntity<DocumentoEnvioStatusResponse> envio(
        @PathVariable UUID tenantId, @PathVariable UUID id
    ) {
        if (!tenantId.equals(TenantContext.getTenantId())) {
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
        return ResponseEntity.ok(documentoEnvioService.statusEnvio(id));
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
