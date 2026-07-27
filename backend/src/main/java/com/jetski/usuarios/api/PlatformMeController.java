package com.jetski.usuarios.api;

import com.jetski.shared.security.PapelPlataforma;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * "Quem sou eu" do operador de plataforma.
 *
 * <p>Existe porque {@code /v1/user/permissions} exige {@code X-Tenant-Id} — e o console
 * não tem empresa corrente. Sem isto o menu do console mostraria itens que o OPA vai negar
 * (Operadores e Configurações são exclusivos de {@code PLATFORM_ADMIN}), que é exatamente
 * a mentira que a F2 tirou do {@code UserPermissionsController}.
 *
 * <p>Ação {@code platform:me}: GET fora da lista exclusiva de admin, logo liberada para
 * qualquer papel de plataforma — é informação sobre o próprio usuário.
 *
 * @since 0.9.0
 */
@RestController
@RequestMapping("/v1/platform/me")
@RequiredArgsConstructor
@Tag(name = "Platform", description = "Operação da plataforma (super admin)")
public class PlatformMeController {

    /**
     * @param usuarioId id interno do operador
     * @param papeis    papéis de plataforma reconhecidos
     * @param admin     atalho para a UI: {@code PLATFORM_ADMIN} presente
     */
    public record OperadorAtual(UUID usuarioId, List<String> papeis, boolean admin) {}

    @GetMapping
    @Operation(summary = "Papéis de plataforma do operador autenticado",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<OperadorAtual> me() {
        List<PapelPlataforma> papeis = PapelPlataforma.filtrar(TenantContext.getUserRoles());
        return ResponseEntity.ok(new OperadorAtual(
            TenantContext.getUsuarioId(),
            papeis.stream().map(Enum::name).toList(),
            papeis.contains(PapelPlataforma.PLATFORM_ADMIN)));
    }
}
