package com.jetski.usuarios.api;

import com.jetski.usuarios.api.dto.ConviteSummaryDTO;
import com.jetski.usuarios.internal.PlatformMembroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Usuários de uma empresa no console da plataforma.
 *
 * <p>Ações OPA (o {@code ActionExtractor} descarta os UUIDs do path):
 * <ul>
 *   <li>{@code platform:tenants:membros} — GET lista (qualquer papel de plataforma);</li>
 *   <li>{@code platform:tenants:membros:desativar|reativar|remover} — POST;</li>
 *   <li>{@code platform:tenants:membros:convites} — GET lista / POST convida;</li>
 *   <li>{@code platform:tenants:membros:convites:cancelar} — POST.</li>
 * </ul>
 * Escritas liberadas a PLATFORM_ADMIN e PLATFORM_SUPORTE ({@code acoes_suporte}).
 */
@RestController
@RequestMapping("/v1/platform/tenants/{tenantId}/membros")
@RequiredArgsConstructor
@Tag(name = "Platform", description = "Operação da plataforma (super admin)")
public class PlatformMembroController {

    private final PlatformMembroService membroService;

    /** Motivo obrigatório das escritas (vai para a auditoria). */
    public record MotivoRequest(String motivo) {}

    public record ConviteRequest(String email, String nome, List<String> papeis, String motivo) {}

    @GetMapping
    @Operation(summary = "Usuários (staff) de uma empresa",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public List<PlatformMembroService.MembroEmpresa> listar(@PathVariable UUID tenantId) {
        return membroService.listar(tenantId);
    }

    @PostMapping("/{usuarioId}/desativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desativar o usuário nesta empresa",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public void desativar(@PathVariable UUID tenantId, @PathVariable UUID usuarioId,
                          @RequestBody(required = false) MotivoRequest req) {
        membroService.desativar(tenantId, usuarioId, motivo(req));
    }

    @PostMapping("/{usuarioId}/reativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reativar o usuário nesta empresa",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public void reativar(@PathVariable UUID tenantId, @PathVariable UUID usuarioId,
                         @RequestBody(required = false) MotivoRequest req) {
        membroService.reativar(tenantId, usuarioId, motivo(req));
    }

    @PostMapping("/{usuarioId}/remover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remover o usuário desta empresa (a conta da pessoa continua)",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public void remover(@PathVariable UUID tenantId, @PathVariable UUID usuarioId,
                        @RequestBody(required = false) MotivoRequest req) {
        membroService.remover(tenantId, usuarioId, motivo(req));
    }

    @GetMapping("/convites")
    @Operation(summary = "Convites pendentes da empresa",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public List<ConviteSummaryDTO> convites(@PathVariable UUID tenantId) {
        return membroService.convites(tenantId);
    }

    @PostMapping("/convites")
    @Operation(summary = "Convidar alguém para a empresa (conta nova ou existente)",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public PlatformMembroService.ConviteCriado convidar(@PathVariable UUID tenantId,
                                                       @RequestBody ConviteRequest req) {
        return membroService.convidar(tenantId, req.email(), req.nome(), req.papeis(), req.motivo());
    }

    @PostMapping("/convites/{conviteId}/cancelar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancelar um convite pendente",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public void cancelarConvite(@PathVariable UUID tenantId, @PathVariable UUID conviteId,
                                @RequestBody(required = false) MotivoRequest req) {
        membroService.cancelarConvite(tenantId, conviteId, motivo(req));
    }

    private static String motivo(MotivoRequest req) {
        return req == null ? null : req.motivo();
    }
}
