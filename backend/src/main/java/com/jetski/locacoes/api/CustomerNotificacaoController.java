package com.jetski.locacoes.api;

import com.jetski.locacoes.internal.CustomerNotificacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Notificações in-app do cliente (backlog P4). Escopo /v1/customers/**.
 */
@RestController
@RequestMapping("/v1/customers/notificacoes")
@RequiredArgsConstructor
@Tag(name = "Portal do Cliente — Notificações", description = "Sininho do portal")
public class CustomerNotificacaoController {

    private final CustomerNotificacaoService service;

    @GetMapping
    @Operation(summary = "Caixa de notificações (todas as lojas; 50 mais recentes + contagem não lidas)")
    public ResponseEntity<CustomerNotificacaoService.Caixa> caixa(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.caixa(jwt.getSubject()));
    }

    @PostMapping("/{id}/lida")
    @Operation(summary = "Marca uma notificação como lida")
    public ResponseEntity<Void> lida(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.marcarLida(jwt.getSubject(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lidas")
    @Operation(summary = "Marca todas como lidas")
    public ResponseEntity<Void> todasLidas(@AuthenticationPrincipal Jwt jwt) {
        service.marcarTodasLidas(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
