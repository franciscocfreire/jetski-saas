package com.jetski.locacoes.api;

import com.jetski.locacoes.internal.PdfLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrega pública (token de uso único) de PDFs gerados — permite abrir o PDF por
 * uma URL https real, compatível com o iOS Safari (que não abre blob: em aba nova).
 * O token é criado por um endpoint autenticado ({@link PdfLinkService#criarLink}).
 */
@RestController
@RequestMapping("/v1/pdf")
@Tag(name = "PDF", description = "Abertura de PDFs por link temporário")
@RequiredArgsConstructor
@Slf4j
public class PublicPdfController {

    private final PdfLinkService pdfLinkService;

    @GetMapping("/{token}")
    @Operation(summary = "Abrir um PDF por token temporário (uso único)")
    public ResponseEntity<byte[]> abrir(@PathVariable String token) {
        byte[] pdf = pdfLinkService.consumir(token);
        if (pdf == null) {
            return ResponseEntity.status(404).build();
        }
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"documento.pdf\"")
            .body(pdf);
    }
}
