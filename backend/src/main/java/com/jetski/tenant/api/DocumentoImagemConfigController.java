package com.jetski.tenant.api;

import com.jetski.tenant.api.dto.ImagemCompressaoConfig;
import com.jetski.tenant.internal.ImagemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Leitura, pelo backoffice do tenant, da config de compressão de imagem definida
 * pela plataforma — aplicada no navegador antes de enviar fotos. Disponível a
 * quem faz upload (ADMIN/GERENTE/OPERADOR). Mesmo padrão do
 * {@code CreditoController.config} (tenant lê valor global de plataforma).
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/documentos/imagem-config")
@RequiredArgsConstructor
@Tag(name = "Documentos", description = "Config de compressão de imagem (leitura pelo tenant)")
public class DocumentoImagemConfigController {

    private final ImagemConfigService imagemConfigService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Presets de compressão de imagem por tipo de documento")
    public ResponseEntity<ImagemCompressaoConfig> get(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(imagemConfigService.get());
    }
}
