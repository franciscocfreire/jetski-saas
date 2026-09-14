package com.jetski.locacoes.api;

import com.jetski.locacoes.internal.InstrutorAssinaturaLinkService;
import com.jetski.locacoes.internal.InstrutorAssinaturaLinkService.AssinaturaRegistrada;
import com.jetski.locacoes.internal.InstrutorAssinaturaLinkService.LinkInfo;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Página pública de assinatura do instrutor (sem autenticação).
 *
 * <p>Sob {@code /v1/public/**} (permitAll no SecurityConfig e sem tenant no TenantFilter);
 * a autorização é o conhecimento do token de uso único gerado pela empresa.
 */
@RestController
@RequestMapping("/v1/public/instrutor-assinatura")
@Tag(name = "Instrutores (público)", description = "Assinatura do instrutor por link único")
@RequiredArgsConstructor
public class InstrutorAssinaturaPublicController {

    private final InstrutorAssinaturaLinkService linkService;

    public record AssinarRequest(String assinaturaBase64) {}

    @GetMapping("/{token}")
    @Operation(summary = "Dados do link de assinatura (instrutor e empresa)")
    public LinkInfo consultar(@PathVariable String token) {
        try {
            return linkService.consultar(token);
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping("/{token}")
    @Operation(summary = "Registrar a assinatura do instrutor (uso único)")
    public AssinaturaRegistrada assinar(@PathVariable String token, @RequestBody AssinarRequest req,
                                        HttpServletRequest http) {
        try {
            return linkService.assinar(token, req == null ? null : req.assinaturaBase64(),
                ipDoCliente(http), http.getHeader("User-Agent"));
        } finally {
            TenantContext.clear();
        }
    }

    /** IP real atrás do Cloudflare/nginx (primeiro da cadeia), como o aceite do cliente. */
    private static String ipDoCliente(HttpServletRequest http) {
        String cf = http.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) {
            return cf.trim();
        }
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
