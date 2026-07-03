package com.jetski.locacoes.api;

import com.jetski.locacoes.internal.CustomerAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Auto-cadastro público do cliente final (portal do cliente).
 *
 * Cria APENAS a identidade global (Keycloak: role CLIENTE, e-mail não verificado
 * + VERIFY_EMAIL). Nenhum Cliente tenant-scoped é criado aqui — isso acontece na
 * primeira reserva com cada loja (P1), sempre com vínculo explícito.
 */
@RestController
@RequestMapping("/v1/public/customers")
@RequiredArgsConstructor
@Tag(name = "Portal do Cliente — Público", description = "Auto-cadastro do cliente final")
public class CustomerSignupController {

    private final CustomerAccountService customerAccountService;

    public record SignupRequest(
        @NotBlank @Size(min = 3, max = 120) String nome,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 128) String senha
    ) {}

    @PostMapping("/signup")
    @Operation(summary = "Cria a conta do cliente (identidade global; e-mail de verificação enviado)")
    public ResponseEntity<Map<String, String>> signup(@Valid @RequestBody SignupRequest request) {
        customerAccountService.signup(request.nome(), request.email(), request.senha());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("message", "Conta criada. Verifique seu e-mail para ativar todos os recursos."));
    }
}
