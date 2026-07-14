package com.jetski.tenant.api;

import com.jetski.tenant.api.dto.CapitaniaRequest;
import com.jetski.tenant.api.dto.CapitaniaResponse;
import com.jetski.tenant.internal.CapitaniaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Manutenção do catálogo de capitanias pelo super admin (V047).
 *
 * <p>Autorização: paths {@code /v1/platform/**} viram ações {@code platform:*}
 * no OPA — apenas usuários com {@code unrestricted_access}.
 *
 * @author Jetski Team
 */
@RestController
@RequestMapping("/v1/platform/capitanias")
@RequiredArgsConstructor
public class PlatformCapitaniaController {

    private final CapitaniaService capitaniaService;

    /** Todas as capitanias, inclusive inativas. */
    @GetMapping
    public List<CapitaniaResponse> listar() {
        return capitaniaService.listarTodas();
    }

    @PostMapping
    public CapitaniaResponse criar(@RequestBody CapitaniaRequest body) {
        return capitaniaService.criar(body);
    }

    @PutMapping("/{id}")
    public CapitaniaResponse atualizar(@PathVariable("id") UUID id,
                                       @RequestBody CapitaniaRequest body) {
        return capitaniaService.atualizar(id, body);
    }
}
