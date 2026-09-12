package com.jetski.tenant.api;

import com.jetski.tenant.internal.PlatformLimiteService;
import com.jetski.tenant.internal.PlatformLimiteService.LimiteUsuarios;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Limite de usuários personalizado por empresa (V068).
 *
 * <p>Ação OPA: {@code platform:tenants:limites:usuarios}. O GET cai na leitura de rotina;
 * o PUT é comercial e fica com {@code PLATFORM_FINANCEIRO} (e admin), junto de
 * {@code platform:tenants:plano} — ver {@code platform.rego}.
 */
@RestController
@RequestMapping("/v1/platform/tenants/{tenantId}/limites/usuarios")
@RequiredArgsConstructor
@Tag(name = "Platform", description = "Operação da plataforma (super admin)")
public class PlatformLimiteController {

    private final PlatformLimiteService limiteService;

    /**
     * @param maximo novo teto de usuários ativos; nulo = voltar a seguir o plano
     * @param motivo obrigatório (auditado)
     */
    public record DefinirLimiteRequest(Integer maximo, String motivo) {}

    @GetMapping
    @Operation(summary = "Limite de usuários da empresa (plano × personalizado)",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public LimiteUsuarios ver(@PathVariable UUID tenantId) {
        return limiteService.usuarios(tenantId);
    }

    @PutMapping
    @Operation(summary = "Definir ou remover o limite de usuários personalizado",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public LimiteUsuarios definir(@PathVariable UUID tenantId,
                                  @RequestBody DefinirLimiteRequest body) {
        return limiteService.definirUsuarios(tenantId, body.maximo(), body.motivo());
    }
}
