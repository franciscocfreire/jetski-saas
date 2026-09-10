package com.jetski.locacoes.api;

import com.jetski.locacoes.internal.PdfLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Entrega pública (por token) de PDFs — permite abrir o PDF por uma URL https real,
 * compatível com o iOS Safari (que não abre blob: em aba nova). O token é criado por
 * um endpoint autenticado ({@link PdfLinkService}).
 *
 * <p>Rota pública de propósito: é ela que torna o link compartilhável. O token é
 * aleatório e expira; quem tiver a URL abre o PDF sem login enquanto ela valer.
 */
@RestController
@RequestMapping("/v1/pdf")
@Tag(name = "PDF", description = "Abertura de PDFs por link temporário")
@RequiredArgsConstructor
@Slf4j
public class PublicPdfController {

    private final PdfLinkService pdfLinkService;

    @GetMapping("/{token}")
    @Operation(
        summary = "Abrir um PDF por token temporário",
        description = "Multiuso até expirar — o token era de uso único e um simples refresh "
                    + "na aba já devolvia 404, o que também impedia compartilhar o link."
    )
    public ResponseEntity<byte[]> abrir(@PathVariable String token) {
        PdfLinkService.Pdf pdf = pdfLinkService.abrir(token);
        if (pdf == null) {
            return ResponseEntity.status(404).build();
        }
        // filename* em UTF-8 (RFC 5987): o nome carrega acentos e espaços
        // ("FELIPE H M 49811586888.pdf") e é o que o navegador exibe na aba e salva.
        ContentDisposition cd = ContentDisposition.inline()
            .filename(pdf.filename(), StandardCharsets.UTF_8)
            .build();
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
            .body(pdf.conteudo());
    }
}
