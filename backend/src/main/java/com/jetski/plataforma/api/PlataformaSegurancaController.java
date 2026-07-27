package com.jetski.plataforma.api;

import com.jetski.plataforma.internal.SegurancaConsoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Política de acesso ao próprio console.
 *
 * <p>Só {@code PLATFORM_ADMIN}, leitura inclusive (matriz em {@code platform.rego}):
 * afrouxar o 2FA da porta da plataforma não é tarefa de suporte nem de financeiro, e a
 * tela que expõe isto (`/configuracoes` no console) já é exclusiva de admin. Manter a
 * leitura aberta criaria uma divergência entre o que o menu mostra e o que a API responde
 * — o tipo de descompasso que já apareceu neste projeto.
 *
 * @since 0.9.0
 */
@RestController
@RequestMapping("/v1/platform/seguranca")
@RequiredArgsConstructor
@Tag(name = "Platform", description = "Operação da plataforma (super admin)")
public class PlataformaSegurancaController {

    private final SegurancaConsoleService service;

    /** @param exigeSempre 2FA a cada login no console */
    public record Toggle(boolean exigeSempre) {}

    @GetMapping("/2fa-console")
    @Operation(summary = "Como está a exigência de 2FA no console",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<SegurancaConsoleService.Estado> consultar() {
        return ResponseEntity.ok(service.consultar());
    }

    @PutMapping("/2fa-console")
    @Operation(summary = "Liga/desliga o 2FA a cada login no console",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<SegurancaConsoleService.Estado> definir(@RequestBody Toggle body) {
        return ResponseEntity.ok(service.definir(body.exigeSempre()));
    }
}
