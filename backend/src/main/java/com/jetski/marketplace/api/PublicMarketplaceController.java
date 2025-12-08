package com.jetski.marketplace.api;

import com.jetski.marketplace.api.dto.MarketplaceModeloDTO;
import com.jetski.marketplace.internal.MarketplaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller: Public Marketplace API
 *
 * Public endpoints for the marketplace (no authentication required).
 * Lists jetski models from ALL tenants that opted into the marketplace.
 *
 * Business Rules:
 * - Only shows models from active tenants (status = ATIVO)
 * - Only shows models where tenant.exibirNoMarketplace = true
 * - Only shows models where modelo.exibirNoMarketplace = true AND modelo.ativo = true
 * - Results ordered by tenant.prioridadeMarketplace (paid placement)
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@RestController
@RequestMapping("/v1/public/marketplace")
@Tag(name = "Marketplace Público", description = "API pública do marketplace - sem autenticação")
@RequiredArgsConstructor
@Slf4j
public class PublicMarketplaceController {

    private final MarketplaceService marketplaceService;

    /**
     * List all models available in the public marketplace.
     *
     * Returns models from all tenants that have opted into the marketplace.
     * Results are ordered by tenant priority (paid placement) and then randomly
     * to provide variety within the same priority level.
     *
     * No authentication required.
     *
     * @return List of marketplace models
     */
    @GetMapping("/modelos")
    @Operation(
        summary = "Listar modelos do marketplace",
        description = "Lista todos os modelos de jetski disponíveis no marketplace público. " +
                      "Retorna modelos de todas as empresas que optaram por aparecer no marketplace. " +
                      "Ordenado por prioridade (destaque pago) e depois aleatório."
    )
    public ResponseEntity<List<MarketplaceModeloDTO>> listModelos() {
        log.info("GET /v1/public/marketplace/modelos");

        List<MarketplaceModeloDTO> modelos = marketplaceService.listPublicModelos();

        log.debug("Marketplace: {} models found", modelos.size());
        return ResponseEntity.ok(modelos);
    }

    /**
     * Get details of a specific model from the marketplace.
     *
     * Returns detailed information about a model if it is visible in the marketplace.
     * Model must belong to an active tenant that has opted into the marketplace.
     *
     * No authentication required.
     *
     * @param id Model UUID
     * @return Model details or 404 if not found/not visible
     */
    @GetMapping("/modelos/{id}")
    @Operation(
        summary = "Obter detalhes de um modelo",
        description = "Retorna os detalhes de um modelo específico do marketplace. " +
                      "O modelo deve pertencer a uma empresa ativa que optou por aparecer no marketplace."
    )
    public ResponseEntity<MarketplaceModeloDTO> getModelo(
        @Parameter(description = "UUID do modelo")
        @PathVariable UUID id
    ) {
        log.info("GET /v1/public/marketplace/modelos/{}", id);

        return marketplaceService.getPublicModelo(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
