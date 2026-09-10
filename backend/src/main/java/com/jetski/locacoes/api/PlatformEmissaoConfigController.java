package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.EmissaoEnvioConfig;
import com.jetski.locacoes.internal.EmissaoEnvioConfigService;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * Kill switch do envio assíncrono dos e-mails da emissão (super admin, global).
 *
 * <p>Sem {@code @PreAuthorize}: a autorização de {@code /v1/platform/**} vem do
 * {@code PlatformScopeInterceptor} + OPA ({@code platform:emissao:envio-config}),
 * mesmo padrão do {@code PlatformImagemConfigController}. Mora no módulo dono do
 * domínio (locacoes), como o {@code PlatformCreditoController} mora em creditos.
 */
@Slf4j
@RestController
@RequestMapping("/v1/platform/emissao/envio-config")
@RequiredArgsConstructor
@Tag(name = "Platform Emissão", description = "Como os e-mails da emissão saem (super admin)")
public class PlatformEmissaoConfigController {

    private final EmissaoEnvioConfigService envioConfigService;

    @GetMapping
    @Operation(summary = "Config vigente do envio de e-mails da emissão")
    public ResponseEntity<EmissaoEnvioConfig> get() {
        return ResponseEntity.ok(envioConfigService.get());
    }

    @PutMapping
    @Operation(
        summary = "Ligar/desligar o envio assíncrono",
        description = "Assíncrono (default): o POST de emissão responde em ~1,5 s e os e-mails "
                    + "saem depois do commit. Síncrono: comportamento antigo, ~25 s de espera "
                    + "para o operador — só como rollback se o assíncrono der problema."
    )
    public ResponseEntity<EmissaoEnvioConfig> atualizar(@RequestBody EmissaoEnvioConfig config) {
        return ResponseEntity.ok(envioConfigService.atualizar(config, actorOrNull()));
    }

    private UUID actorOrNull() {
        try {
            return TenantContext.getUsuarioId();
        } catch (Exception e) {
            return null;
        }
    }
}
