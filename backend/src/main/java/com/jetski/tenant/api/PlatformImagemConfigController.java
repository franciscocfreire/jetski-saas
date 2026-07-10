package com.jetski.tenant.api;

import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.api.dto.ImagemCompressaoConfig;
import com.jetski.tenant.internal.ImagemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Config de compressão de imagem por tipo de documento (super admin, global à
 * plataforma). Autorização por {@code platform:*} no OPA (só acesso irrestrito),
 * mesmo padrão do {@code PlatformCreditoController}.
 */
@Slf4j
@RestController
@RequestMapping("/v1/platform/documentos/imagem-config")
@RequiredArgsConstructor
@Tag(name = "Platform Imagem", description = "Qualidade de compressão de imagem por documento (super admin)")
public class PlatformImagemConfigController {

    private final ImagemConfigService imagemConfigService;

    @GetMapping
    @Operation(summary = "Config de compressão de imagem vigente")
    public ResponseEntity<ImagemCompressaoConfig> get() {
        return ResponseEntity.ok(imagemConfigService.get());
    }

    @PutMapping
    @Operation(summary = "Atualizar a qualidade/resolução de compressão por tipo de documento")
    public ResponseEntity<ImagemCompressaoConfig> atualizar(
            @Valid @RequestBody ImagemCompressaoConfig config) {
        UUID actor = actorOrNull();
        log.info("PUT /v1/platform/documentos/imagem-config por {}", actor);
        return ResponseEntity.ok(imagemConfigService.atualizar(config, actor));
    }

    private UUID actorOrNull() {
        try {
            return TenantContext.getUsuarioId();
        } catch (Exception e) {
            return null;
        }
    }
}
