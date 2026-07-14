package com.jetski.tenant.api;

import com.jetski.tenant.api.dto.CapitaniaResponse;
import com.jetski.tenant.internal.CapitaniaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catálogo de capitanias para qualquer usuário autenticado (V047):
 * usado no cadastro/configuração da empresa (emissora declara a capitania
 * da licença; operadora, a da área de operação).
 *
 * <p>Autorização: como {@code /v1/user/tenants}, é catálogo sem tenant
 * específico — a ação {@code capitania:list} é isenta do ABAC (só exige
 * autenticação; ver {@code ABACAuthorizationInterceptor#isPublicEndpoint}).
 *
 * @author Jetski Team
 */
@RestController
@RequestMapping("/v1/capitanias")
@RequiredArgsConstructor
@Tag(name = "Capitanias", description = "Catálogo de Capitanias da Marinha")
public class CapitaniaController {

    private final CapitaniaService capitaniaService;

    @GetMapping
    @Operation(summary = "Lista capitanias ativas do catálogo")
    public List<CapitaniaResponse> listar() {
        return capitaniaService.listarAtivas();
    }
}
