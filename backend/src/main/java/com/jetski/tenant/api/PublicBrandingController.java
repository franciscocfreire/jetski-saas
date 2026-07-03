package com.jetski.tenant.api;

import com.jetski.tenant.domain.Branding;
import com.jetski.tenant.internal.TenantConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Branding público da loja (P4 do portal): cores + logo para o white-label
 * nas páginas da loja no portal do cliente. Sem autenticação — só lojas
 * visíveis no marketplace.
 */
@RestController
@RequestMapping("/v1/public/lojas")
@RequiredArgsConstructor
@Tag(name = "Marketplace Público", description = "Branding da loja (sem autenticação)")
public class PublicBrandingController {

    private final TenantConfigService tenantConfigService;
    private final EntityManager entityManager;

    public record BrandingPublico(String corPrimaria, String corSecundaria, String logoDataUrl) {}

    @GetMapping("/{slug}/branding")
    @Operation(summary = "Cores e logo da loja (white-label do portal)")
    public ResponseEntity<BrandingPublico> branding(@PathVariable String slug) {
        @SuppressWarnings("unchecked")
        List<UUID> ids = entityManager.createNativeQuery(
                "SELECT id FROM tenant WHERE slug = :slug AND status = 'ATIVO' " +
                "AND exibir_no_marketplace = true")
            .setParameter("slug", slug)
            .getResultList();
        if (ids.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        UUID tenantId = ids.get(0);
        Branding b = tenantConfigService.getBranding(tenantId);
        return ResponseEntity.ok(new BrandingPublico(
            b.corPrimaria(), b.corSecundaria(),
            tenantConfigService.getLogoDataUrl(tenantId)));
    }
}
