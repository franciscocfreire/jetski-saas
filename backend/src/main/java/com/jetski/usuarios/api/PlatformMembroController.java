package com.jetski.usuarios.api;

import com.jetski.usuarios.internal.PlatformMembroService;
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
 * Usuários de uma empresa no console da plataforma (somente leitura).
 *
 * <p>Ação OPA: {@code platform:tenants:membros} (o UUID do path é descartado pelo
 * {@code ActionExtractor}). Só existe GET, que o {@code platform.rego} libera a qualquer
 * papel de plataforma pelo ramo de leitura de rotina.
 */
@RestController
@RequestMapping("/v1/platform/tenants/{tenantId}/membros")
@RequiredArgsConstructor
@Tag(name = "Platform", description = "Operação da plataforma (super admin)")
public class PlatformMembroController {

    private final PlatformMembroService membroService;

    @GetMapping
    @Operation(summary = "Usuários (staff) de uma empresa",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public List<PlatformMembroService.MembroEmpresa> listar(@PathVariable UUID tenantId) {
        return membroService.listar(tenantId);
    }
}
