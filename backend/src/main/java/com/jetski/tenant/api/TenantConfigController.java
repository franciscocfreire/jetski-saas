package com.jetski.tenant.api;

import com.jetski.tenant.api.dto.BrandingRequest;
import com.jetski.tenant.api.dto.BrandingResponse;
import com.jetski.tenant.api.dto.ComissaoConfigRequest;
import com.jetski.tenant.api.dto.ComissaoConfigResponse;
import com.jetski.tenant.api.dto.TenantGeralConfigRequest;
import com.jetski.tenant.api.dto.TenantGeralConfigResponse;
import com.jetski.tenant.domain.AssinaturaConfig;
import com.jetski.tenant.domain.Branding;
import com.jetski.tenant.domain.ComissaoConfig;
import com.jetski.tenant.domain.DocumentoConfig;
import com.jetski.tenant.internal.TenantConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Controller: TenantConfigController
 *
 * REST API for managing tenant configuration settings.
 * Accessible only by ADMIN_TENANT and GERENTE roles.
 *
 * Endpoints:
 * - GET  /v1/tenants/{tenantId}/config/comissao - Get commission config
 * - PUT  /v1/tenants/{tenantId}/config/comissao - Update commission config
 *
 * @author Jetski Team
 * @since 0.12.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/config")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tenant Config", description = "Configurações do tenant")
public class TenantConfigController {

    private final TenantConfigService tenantConfigService;

    /**
     * Get the current commission and bonus configuration for the tenant.
     */
    @GetMapping("/comissao")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Obter configuração de comissões",
        description = "Retorna a configuração atual de percentuais de comissão e bônus do tenant."
    )
    public ResponseEntity<ComissaoConfigResponse> getComissaoConfig(
            @Parameter(description = "UUID do tenant")
            @PathVariable UUID tenantId) {

        log.info("GET /v1/tenants/{}/config/comissao", tenantId);

        ComissaoConfig config = tenantConfigService.getComissaoConfig(tenantId);
        return ResponseEntity.ok(toResponse(config));
    }

    /**
     * Update the commission and bonus configuration for the tenant.
     */
    @PutMapping("/comissao")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Atualizar configuração de comissões",
        description = "Atualiza os percentuais de comissão e configuração de bônus do tenant. " +
                      "Apenas ADMIN_TENANT e GERENTE podem alterar."
    )
    public ResponseEntity<ComissaoConfigResponse> updateComissaoConfig(
            @Parameter(description = "UUID do tenant")
            @PathVariable UUID tenantId,
            @Valid @RequestBody ComissaoConfigRequest request) {

        log.info("PUT /v1/tenants/{}/config/comissao - percentualPadrao={}, percentualAbaixoBase={}, bonusAtivo={}",
                tenantId, request.getPercentualPadrao(), request.getPercentualAbaixoBase(), request.getBonusAtivo());

        ComissaoConfig config = tenantConfigService.updateComissaoConfig(tenantId, request);
        return ResponseEntity.ok(toResponse(config));
    }

    @GetMapping("/geral")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Obter dados gerais/e-mail da empresa")
    public ResponseEntity<TenantGeralConfigResponse> getGeralConfig(@PathVariable UUID tenantId) {
        log.info("GET /v1/tenants/{}/config/geral", tenantId);
        return ResponseEntity.ok(tenantConfigService.getGeralConfig(tenantId));
    }

    @PutMapping("/geral")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Atualizar dados gerais/e-mail da empresa",
        description = "Atualiza razão social, cidade, e-mail da Marinha (destino dos documentos) " +
                      "e e-mail remetente. Apenas ADMIN_TENANT e GERENTE."
    )
    public ResponseEntity<TenantGeralConfigResponse> updateGeralConfig(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TenantGeralConfigRequest request) {
        log.info("PUT /v1/tenants/{}/config/geral", tenantId);
        return ResponseEntity.ok(tenantConfigService.updateGeralConfig(tenantId, request));
    }

    // ========== PERFIL DE EMISSÃO / EAMA (V047) ==========

    @GetMapping("/emissora")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Obter perfil de emissão (capitania + registro EAMA + habilitação)")
    public ResponseEntity<com.jetski.tenant.api.dto.EmissoraConfigResponse> getEmissoraConfig(
            @PathVariable UUID tenantId) {
        log.info("GET /v1/tenants/{}/config/emissora", tenantId);
        return ResponseEntity.ok(tenantConfigService.getEmissoraConfig(tenantId));
    }

    @PutMapping("/emissora")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Atualizar capitania e registro EAMA da empresa",
        description = "A habilitação de emissora é validada pelo super admin; alterar capitania "
                    + "ou registro com a empresa já habilitada derruba a habilitação (revalidação)."
    )
    public ResponseEntity<com.jetski.tenant.api.dto.EmissoraConfigResponse> updateEmissoraConfig(
            @PathVariable UUID tenantId,
            @RequestBody com.jetski.tenant.api.dto.EmissoraConfigRequest request) {
        log.info("PUT /v1/tenants/{}/config/emissora", tenantId);
        return ResponseEntity.ok(tenantConfigService.updateEmissoraConfig(tenantId, request));
    }

    @GetMapping("/documento")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Obter parametrização de emissão de documentos",
        description = "Retorna o que vai em cada destino (Marinha vs Cliente) na emissão."
    )
    public ResponseEntity<DocumentoConfig> getDocumentoConfig(@PathVariable UUID tenantId) {
        log.info("GET /v1/tenants/{}/config/documento", tenantId);
        return ResponseEntity.ok(tenantConfigService.getDocumentoConfig(tenantId));
    }

    @PutMapping("/documento")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Atualizar parametrização de emissão de documentos",
        description = "Define, por seção, o que é enviado à Marinha e ao cliente. Apenas ADMIN_TENANT e GERENTE."
    )
    public ResponseEntity<DocumentoConfig> updateDocumentoConfig(
            @PathVariable UUID tenantId,
            @RequestBody DocumentoConfig request) {
        log.info("PUT /v1/tenants/{}/config/documento", tenantId);
        return ResponseEntity.ok(tenantConfigService.updateDocumentoConfig(tenantId, request));
    }

    @GetMapping("/assinatura")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Obter configuração de assinatura (auditoria + carimbo de tempo)")
    public ResponseEntity<AssinaturaConfig> getAssinaturaConfig(@PathVariable UUID tenantId) {
        log.info("GET /v1/tenants/{}/config/assinatura", tenantId);
        return ResponseEntity.ok(tenantConfigService.getAssinaturaConfig(tenantId));
    }

    @PutMapping("/assinatura")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Atualizar configuração de assinatura. Apenas ADMIN_TENANT e GERENTE.")
    public ResponseEntity<AssinaturaConfig> updateAssinaturaConfig(
            @PathVariable UUID tenantId,
            @RequestBody AssinaturaConfig request) {
        log.info("PUT /v1/tenants/{}/config/assinatura", tenantId);
        return ResponseEntity.ok(tenantConfigService.updateAssinaturaConfig(tenantId, request));
    }

    // ========== BRANDING (white-label) ==========

    @GetMapping("/branding")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Obter branding do tenant (cores + logo como data URL)")
    public ResponseEntity<BrandingResponse> getBranding(@PathVariable UUID tenantId) {
        log.info("GET /v1/tenants/{}/config/branding", tenantId);
        return ResponseEntity.ok(toBrandingResponse(tenantId, tenantConfigService.getBranding(tenantId)));
    }

    @PutMapping("/branding")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Atualizar cores e conteúdo da vitrine. Nulos voltam ao padrão Meu Jet.")
    public ResponseEntity<BrandingResponse> updateBranding(
            @PathVariable UUID tenantId,
            @RequestBody BrandingRequest request) {
        log.info("PUT /v1/tenants/{}/config/branding", tenantId);
        Branding cfg = tenantConfigService.updateBranding(tenantId,
            new Branding(request.corPrimaria(), request.corSecundaria(), null, null,
                request.vitrineDescricao(), request.vitrineEndereco(), request.vitrinePraia(),
                request.vitrineHorario(), request.vitrineInstagram(), request.vitrineSite()));
        return ResponseEntity.ok(toBrandingResponse(tenantId, cfg));
    }

    @PostMapping(value = "/branding/logo", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Enviar logo do tenant (PNG/JPEG/WebP, máx. 512 KB)")
    public ResponseEntity<BrandingResponse> uploadBrandingLogo(
            @PathVariable UUID tenantId,
            @RequestParam("file") MultipartFile file) throws IOException {
        log.info("POST /v1/tenants/{}/config/branding/logo ({} bytes)", tenantId, file.getSize());
        Branding cfg = tenantConfigService.uploadLogo(tenantId, file.getBytes(), file.getContentType());
        return ResponseEntity.ok(toBrandingResponse(tenantId, cfg));
    }

    @DeleteMapping("/branding/logo")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Remover logo do tenant (volta ao padrão Meu Jet)")
    public ResponseEntity<BrandingResponse> deleteBrandingLogo(@PathVariable UUID tenantId) {
        log.info("DELETE /v1/tenants/{}/config/branding/logo", tenantId);
        Branding cfg = tenantConfigService.removeLogo(tenantId);
        return ResponseEntity.ok(toBrandingResponse(tenantId, cfg));
    }

    private BrandingResponse toBrandingResponse(UUID tenantId, Branding cfg) {
        String logoDataUrl = cfg.temLogo() ? tenantConfigService.getLogoDataUrl(tenantId) : null;
        return new BrandingResponse(cfg.corPrimaria(), cfg.corSecundaria(), logoDataUrl,
            cfg.vitrineDescricao(), cfg.vitrineEndereco(), cfg.vitrinePraia(),
            cfg.vitrineHorario(), cfg.vitrineInstagram(), cfg.vitrineSite());
    }

    /**
     * Convert domain config to response DTO.
     */
    private ComissaoConfigResponse toResponse(ComissaoConfig config) {
        return ComissaoConfigResponse.builder()
            .percentualPadrao(config.percentualPadrao())
            .percentualAbaixoBase(config.percentualAbaixoBase())
            .bonusAtivo(config.bonusAtivo())
            .bonusMetaVendas(config.bonusMetaVendas())
            .bonusValor(config.bonusValor())
            .build();
    }
}
