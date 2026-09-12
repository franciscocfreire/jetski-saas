package com.jetski.signup.api;

import com.jetski.signup.internal.PlatformSignupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Pedido de cadastro da empresa no console da plataforma (somente leitura).
 *
 * <p>Ação OPA: {@code platform:tenants:solicitacoes} (o UUID do path é descartado pelo
 * {@code ActionExtractor}). Só GET, liberado a qualquer papel de plataforma pelo ramo de
 * leitura de rotina do {@code platform.rego}.
 */
@RestController
@RequestMapping("/v1/platform/tenants/{tenantId}/solicitacoes")
@RequiredArgsConstructor
@Tag(name = "Platform", description = "Operação da plataforma (super admin)")
public class PlatformSignupController {

    private final PlatformSignupService signupService;

    @GetMapping
    @Operation(summary = "Quem pediu o cadastro da empresa",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public List<PlatformSignupService.SolicitacaoCadastro> listar(@PathVariable UUID tenantId) {
        return signupService.listar(tenantId);
    }
}
