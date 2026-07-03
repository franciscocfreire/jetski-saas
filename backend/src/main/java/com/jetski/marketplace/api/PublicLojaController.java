package com.jetski.marketplace.api;

import com.jetski.marketplace.api.dto.MarketplaceModeloDTO;
import com.jetski.marketplace.internal.MarketplaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Vitrine pública POR LOJA (portal do cliente, P1): /loja/{slug}.
 *
 * Mesmas regras de visibilidade do marketplace global (tenant ATIVO +
 * exibir_no_marketplace, via RLS marketplace_public_read). Sem auth.
 */
@RestController
@RequestMapping("/v1/public/lojas")
@RequiredArgsConstructor
@Tag(name = "Marketplace Público", description = "Vitrine por loja (sem autenticação)")
public class PublicLojaController {

    private final MarketplaceService marketplaceService;

    @GetMapping("/{slug}")
    @Operation(summary = "Dados públicos da loja (cabeçalho da vitrine)")
    public ResponseEntity<MarketplaceService.MarketplaceLojaDTO> loja(@PathVariable String slug) {
        return marketplaceService.getPublicLoja(slug)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{slug}/modelos")
    @Operation(summary = "Modelos visíveis da loja")
    public ResponseEntity<List<MarketplaceModeloDTO>> modelos(@PathVariable String slug) {
        return ResponseEntity.ok(marketplaceService.listPublicModelosByLoja(slug));
    }
}
