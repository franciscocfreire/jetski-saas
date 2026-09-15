package com.jetski.tenant.api;

import com.jetski.tenant.internal.CondicaoComercialService;
import com.jetski.tenant.internal.CondicaoComercialService.Condicao;
import com.jetski.tenant.internal.CondicaoComercialService.NovaCondicao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Condição comercial da mensalidade por empresa (V076).
 *
 * <p>Ações OPA: {@code platform:tenants:condicoes} (GET cai na leitura de rotina; POST é
 * comercial) e {@code platform:tenants:condicoes:encerrar} — ambas com
 * {@code PLATFORM_FINANCEIRO} (e admin), junto de trocar plano. Ver {@code platform.rego}.
 */
@RestController
@RequestMapping("/v1/platform/tenants/{tenantId}/condicoes")
@RequiredArgsConstructor
@Tag(name = "Platform", description = "Operação da plataforma (super admin)")
public class PlatformCondicaoController {

    private final CondicaoComercialService condicaoService;

    public record EncerrarRequest(String motivo) {}

    @GetMapping
    @Operation(summary = "Condições comerciais da empresa (vigente, agendadas e histórico)",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public List<Condicao> listar(@PathVariable UUID tenantId) {
        return condicaoService.historico(tenantId);
    }

    @PostMapping
    @Operation(summary = "Conceder isenção/desconto da mensalidade (motivo obrigatório, auditado)",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public Condicao conceder(@PathVariable UUID tenantId, @RequestBody NovaCondicao body) {
        return condicaoService.conceder(tenantId, body);
    }

    @PostMapping("/{condicaoId}/encerrar")
    @Operation(summary = "Encerrar hoje a condição comercial (o histórico fica)",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public Condicao encerrar(@PathVariable UUID tenantId, @PathVariable UUID condicaoId,
                             @RequestBody EncerrarRequest body) {
        return condicaoService.encerrar(tenantId, condicaoId, body.motivo());
    }
}
