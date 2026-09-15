package com.jetski.locacoes.api;

import com.jetski.locacoes.internal.ModeloMidiaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Imagens de modelo enviadas por upload, sem login — é a url que marketplace, vitrine,
 * portal e backoffice usam em {@code <img>}. O arquivo de uma mídia nunca muda (trocar
 * a foto cria outra mídia), então a resposta é cacheável por um ano.
 */
@RestController
@RequestMapping("/v1/public/midias")
@Tag(name = "Público — mídias de modelo", description = "Imagens de modelos enviadas por upload")
@RequiredArgsConstructor
public class PublicModeloMidiaController {

    private final ModeloMidiaService midiaService;

    @GetMapping("/{tenantId}/{midiaId}")
    @Operation(summary = "Imagem de modelo enviada por upload",
        description = "Devolve o arquivo guardado no storage da empresa. 404 se a mídia não existe, "
            + "é de outra empresa ou é uma URL externa.")
    public ResponseEntity<byte[]> arquivo(@PathVariable UUID tenantId, @PathVariable UUID midiaId) {
        return midiaService.lerArquivoPublico(tenantId, midiaId)
            .map(a -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(a.conteudo()))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
