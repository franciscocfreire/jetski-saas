package com.jetski.usuarios.api;

import com.jetski.shared.internal.TenantFilter;
import com.jetski.shared.security.SessaoSuporte;
import com.jetski.shared.security.TenantContext;
import com.jetski.usuarios.internal.SessaoSuporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sessão de suporte: abrir (console), resgatar (backoffice), consultar e encerrar.
 *
 * <p>Duas famílias de rota de propósito:
 * <ul>
 *   <li>{@code /v1/platform/suporte/**} — do CONSOLE. Ação {@code platform:suporte:*},
 *       governada pela matriz de papéis.</li>
 *   <li>{@code /v1/suporte/**} — do BACKOFFICE, onde o operador já está dentro da sessão
 *       (resgate e encerramento). Não exigem papel de plataforma no header porque quem
 *       autoriza é o próprio cookie/código.</li>
 * </ul>
 *
 * @since 0.9.0
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Suporte", description = "Sessão de suporte de plataforma")
public class SessaoSuporteController {

    private final SessaoSuporteService service;

    public record AbrirRequest(String motivo, Boolean somenteLeitura) {}

    /** @param codigo código de uso único para o handoff console → backoffice */
    public record AbrirResponse(UUID sessaoId, String codigo, Instant expiraEm) {}

    /**
     * @param tenant dados da empresa alvo (slug, razão social, status) — o operador não é
     *               membro dela, então o backoffice não conseguiria obtê-los sozinho
     */
    public record SessaoAtualResponse(
        UUID id, UUID tenantId, boolean somenteLeitura, java.util.Map<String, Object> tenant) {}

    // ===================== console =====================

    @PostMapping("/v1/platform/tenants/{tenantId}/suporte")
    @Operation(summary = "Abrir sessão de suporte numa empresa",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<AbrirResponse> abrir(
            @PathVariable UUID tenantId,
            @RequestBody AbrirRequest request,
            HttpServletRequest http) {
        var abertura = service.abrir(
            tenantId,
            request.motivo(),
            request.somenteLeitura() == null || request.somenteLeitura(),   // padrão: leitura
            ipDe(http),
            http.getHeader("User-Agent"));
        return ResponseEntity.ok(new AbrirResponse(
            abertura.sessaoId(), abertura.codigo(), abertura.expiraEm()));
    }

    @GetMapping("/v1/platform/suporte")
    @Operation(summary = "Trilha de sessões de suporte",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<List<SessaoSuporteService.Registro>> trilha(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "50") int limite) {
        return ResponseEntity.ok(service.listar(tenantId, Math.min(limite, 200)));
    }

    @DeleteMapping("/v1/platform/suporte/{sessaoId}")
    @Operation(summary = "Revogar sessão de suporte",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<Void> revogar(@PathVariable UUID sessaoId) {
        service.encerrar(sessaoId);
        return ResponseEntity.noContent().build();
    }

    // ===================== backoffice =====================

    /**
     * Troca o código pelo cookie. Rota pública no {@code TenantFilter}: quem autoriza é o
     * próprio código de uso único, não um papel — o operador ainda não tem sessão aqui.
     */
    @PostMapping("/v1/suporte/resgatar")
    @Operation(summary = "Resgatar código e abrir a sessão no backoffice")
    public ResponseEntity<Void> resgatar(@RequestParam String codigo,
                                         HttpServletRequest http,
                                         HttpServletResponse response) {
        String token = service.resgatar(codigo);
        boolean seguro = http.isSecure()
            || "https".equalsIgnoreCase(http.getHeader("X-Forwarded-Proto"));
        ResponseCookie cookie = ResponseCookie.from(TenantFilter.COOKIE_SUPORTE, token)
            .httpOnly(true)         // o front nunca lê o token
            .secure(seguro)
            .sameSite("Lax")        // o handoff chega por navegação de outro subdomínio
            .path("/")
            .maxAge(Duration.ofMinutes(30))
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.noContent().build();
    }

    /** Quem sou eu nesta sessão — alimenta o banner do backoffice. */
    @GetMapping("/v1/suporte/atual")
    @Operation(summary = "Sessão de suporte do request corrente")
    public ResponseEntity<SessaoAtualResponse> atual() {
        SessaoSuporte s = TenantContext.getSessaoSuporte();
        if (s == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(new SessaoAtualResponse(
            s.id(), s.tenantId(), s.somenteLeitura(), service.empresaDaSessao(s.tenantId())));
    }

    /** Sair do modo suporte: encerra a sessão e apaga o cookie. */
    @PostMapping("/v1/suporte/sair")
    @Operation(summary = "Encerrar a sessão de suporte corrente")
    public ResponseEntity<Void> sair(HttpServletRequest http, HttpServletResponse response) {
        SessaoSuporte s = TenantContext.getSessaoSuporte();
        if (s != null) {
            service.encerrar(s.id());
        }
        boolean seguro = http.isSecure()
            || "https".equalsIgnoreCase(http.getHeader("X-Forwarded-Proto"));
        ResponseCookie apagar = ResponseCookie.from(TenantFilter.COOKIE_SUPORTE, "")
            .httpOnly(true).secure(seguro).sameSite("Lax").path("/").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, apagar.toString());
        return ResponseEntity.noContent().build();
    }

    private static String ipDe(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
