package com.jetski.locacoes.api;

import com.jetski.locacoes.internal.CustomerAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Perfil "self" do cliente final autenticado (portal).
 *
 * Escopo /v1/customers/**: exige role CLIENTE (SecurityConfig) e ação OPA
 * customer:* — sem X-Tenant-Id (o cliente é multi-loja; vínculos por tenant
 * são resolvidos internamente via cliente_identity_provider).
 */
@RestController
@RequestMapping("/v1/customers/self")
@RequiredArgsConstructor
@Tag(name = "Portal do Cliente — Conta", description = "Perfil e vínculos do cliente autenticado")
public class CustomerSelfController {

    private final CustomerAccountService customerAccountService;

    public record AtualizarPerfilRequest(
        @NotBlank @Size(min = 3, max = 120) String nome
    ) {}

    @Value
    @Builder
    public static class SelfResponse {
        String nome;
        String email;
        boolean emailVerified;
        List<CustomerAccountService.VinculoLoja> lojas;
    }

    @GetMapping
    @Operation(summary = "Perfil do cliente autenticado + lojas vinculadas")
    public ResponseEntity<SelfResponse> self(@AuthenticationPrincipal Jwt jwt) {
        Boolean verified = jwt.getClaimAsBoolean("email_verified");
        return ResponseEntity.ok(SelfResponse.builder()
            .nome(jwt.getClaimAsString("name"))
            .email(jwt.getClaimAsString("email"))
            .emailVerified(Boolean.TRUE.equals(verified))
            .lojas(customerAccountService.vinculos(jwt.getSubject()))
            .build());
    }

    @PutMapping
    @Operation(summary = "Atualiza o nome do cliente (identidade global)")
    public ResponseEntity<Void> atualizar(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AtualizarPerfilRequest request) {
        customerAccountService.atualizarNome(jwt.getSubject(), request.nome());
        return ResponseEntity.noContent().build();
    }
}
