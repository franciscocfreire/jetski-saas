package com.jetski.shared.authorization;

import com.jetski.shared.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Barreira de escopo para as rotas {@code /v1/platform/**} (console da plataforma).
 *
 * <p><strong>Por que existe:</strong> até aqui, nenhum controller {@code Platform*} tinha
 * {@code @PreAuthorize} nem checagem em Java — o único gate eram as políticas OPA. Uma
 * regra {@code .rego} editada errado (ou o {@code not startswith(input.action, "platform:")}
 * do {@code authorization.rego} removido por engano) abriria dezenas de endpoints de
 * plataforma de uma vez, incluindo reset e exclusão de empresa.
 *
 * <p>Este interceptor é defesa em profundidade, não substituto: roda <em>antes</em> do
 * {@link ABACAuthorizationInterceptor} (order menor) e barra qualquer request de plataforma
 * de quem não tem papel de plataforma. Casa por padrão de path, então cobre também
 * endpoints futuros — não depende de ninguém lembrar de anotar o método.
 *
 * <p>O contexto é populado pelo {@code TenantFilter} (branch de plataforma), que resolve os
 * papéis globais pelo JWT sem exigir {@code X-Tenant-Id}.
 *
 * @see com.jetski.shared.security.PlatformAccessInfo
 */
@Slf4j
@Component
public class PlatformScopeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Não autenticado: quem responde 401 é o SecurityConfig, não este interceptor.
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return true;
        }

        // F0: acesso de plataforma == unrestricted_access. Na F2 (papéis granulares) esta
        // checagem passa a consultar os papéis PLATFORM_* do contexto.
        if (!TenantContext.isUnrestricted()) {
            log.warn("Platform scope DENY: path={}, method={}, user={}",
                request.getRequestURI(), request.getMethod(), authentication.getName());
            throw new AccessDeniedException(
                "Acesso restrito a administradores de plataforma");
        }

        return true;
    }
}
