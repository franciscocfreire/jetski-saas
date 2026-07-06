package com.jetski.locacoes.api;

import com.jetski.locacoes.internal.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * API pública (sem autenticação) para o cliente ativar sua conta validando o
 * claim-token recebido por e-mail/SMS/WhatsApp. O sucesso provisiona o usuário
 * no Keycloak (role CLIENTE, sem Membro) e vincula a identidade.
 *
 * <p>Sob {@code /v1/public/**} (permitAll); requisição anônima → o interceptor
 * ABAC é ignorado. A autorização é o próprio conhecimento do token + senha.
 */
@RestController
@RequestMapping("/v1/public/clientes/claim")
@Tag(name = "Clientes (público)", description = "Ativação de conta pelo cliente")
@RequiredArgsConstructor
@Slf4j
public class ClaimPublicController {

    private final ClaimService claimService;

    public record ValidarRequest(
        @NotBlank String token,
        @NotBlank String senhaTemporaria
    ) {}

    /** contaExistente=true → o cliente já tinha conta no portal e a senha dele continua valendo. */
    public record ValidarResponse(
        UUID clienteId, String providerUserId, boolean ativada, boolean contaExistente) {}

    @PostMapping("/validar")
    @Operation(summary = "Validar claim-token e ativar a conta do cliente")
    public ResponseEntity<ValidarResponse> validar(@RequestBody ValidarRequest req) {
        log.info("POST /v1/public/clientes/claim/validar token=***");
        ClaimService.AtivacaoResult r = claimService.validar(req.token(), req.senhaTemporaria());
        return ResponseEntity.ok(new ValidarResponse(
            r.getClienteId(), r.getProviderUserId(), true, r.isContaExistente()));
    }
}
