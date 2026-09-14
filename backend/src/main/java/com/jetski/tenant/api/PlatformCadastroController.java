package com.jetski.tenant.api;

import com.jetski.tenant.internal.PlatformCadastroService;
import com.jetski.tenant.internal.PlatformCadastroService.AlteracaoCadastro;
import com.jetski.tenant.internal.PlatformCadastroService.CadastroEmpresa;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Cadastro da empresa no console da plataforma.
 *
 * <p>Ação OPA {@code platform:tenants:cadastro}: GET para qualquer papel de plataforma
 * (ramo de leitura); PUT para PLATFORM_ADMIN e PLATFORM_SUPORTE ({@code acoes_suporte}).
 * Path próprio de propósito: {@code PUT /v1/platform/tenants/{id}} colapsaria na ação
 * {@code platform:tenants} da listagem.
 */
@RestController
@RequestMapping("/v1/platform/tenants/{tenantId}/cadastro")
@RequiredArgsConstructor
@Tag(name = "Platform", description = "Operação da plataforma (super admin)")
public class PlatformCadastroController {

    private final PlatformCadastroService cadastroService;

    @GetMapping
    @Operation(summary = "Cadastro da empresa",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public CadastroEmpresa ver(@PathVariable UUID tenantId) {
        return cadastroService.ver(tenantId);
    }

    @PutMapping
    @Operation(summary = "Alterar o cadastro da empresa (motivo obrigatório; slug não muda)",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public CadastroEmpresa alterar(@PathVariable UUID tenantId, @RequestBody AlteracaoCadastro req) {
        return cadastroService.alterar(tenantId, req);
    }
}
