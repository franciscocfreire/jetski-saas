package com.jetski.usuarios.api;

import com.jetski.shared.security.PapelPlataforma;
import com.jetski.usuarios.internal.PlatformOperadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Operadores da PLATAFORMA — quem administra os administradores.
 *
 * <p>Todas as rotas resolvem para a ação {@code platform:operadores} (o identificador do
 * path é descartado pelo {@code ActionExtractor}), que o {@code platform.rego} restringe a
 * {@code PLATFORM_ADMIN} — inclusive a listagem: saber quem opera a plataforma já é
 * informação sensível.
 *
 * @since 0.9.0
 */
@RestController
@RequestMapping("/v1/platform/operadores")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Platform", description = "Operação da plataforma (super admin)")
public class PlatformOperadorController {

    private final PlatformOperadorService operadorService;

    /** @param email e-mail de uma conta JÁ existente e ativada */
    public record ConcederRequest(
        @NotBlank @Email String email,
        List<String> papeis) {}

    public record PapeisRequest(List<String> papeis) {}

    /** Catálogo de papéis com rótulo e descrição — alimenta a tela sem duplicar texto. */
    public record PapelInfo(String key, String rotulo, String descricao) {}

    @GetMapping
    @Operation(summary = "Operadores da plataforma",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<List<PlatformOperadorService.Operador>> listar() {
        return ResponseEntity.ok(operadorService.listar());
    }

    @GetMapping("/papeis")
    @Operation(summary = "Catálogo de papéis de plataforma",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<List<PapelInfo>> papeis() {
        return ResponseEntity.ok(Arrays.stream(PapelPlataforma.values())
            .map(p -> new PapelInfo(p.name(), p.getRotulo(), p.getDescricao()))
            .toList());
    }

    @PostMapping
    @Operation(summary = "Conceder acesso de plataforma a uma conta existente",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<PlatformOperadorService.Operador> conceder(
            @RequestBody ConcederRequest request) {
        return ResponseEntity.ok(
            operadorService.conceder(request.email().trim(), request.papeis()));
    }

    @PutMapping("/{usuarioId}")
    @Operation(summary = "Atualizar os papéis de um operador",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<PlatformOperadorService.Operador> atualizar(
            @PathVariable UUID usuarioId, @RequestBody PapeisRequest request) {
        return ResponseEntity.ok(operadorService.atualizar(usuarioId, request.papeis()));
    }

    @DeleteMapping("/{usuarioId}")
    @Operation(summary = "Revogar todo o acesso de plataforma",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<Void> revogar(@PathVariable UUID usuarioId) {
        operadorService.revogar(usuarioId);
        return ResponseEntity.noContent().build();
    }
}
