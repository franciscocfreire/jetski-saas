package com.jetski.tenant.api;

import com.jetski.tenant.api.dto.ComissaoConfigRequest;
import com.jetski.tenant.api.dto.ComissaoConfigResponse;
import com.jetski.tenant.api.dto.TenantGeralConfigRequest;
import com.jetski.tenant.api.dto.TenantGeralConfigResponse;
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
