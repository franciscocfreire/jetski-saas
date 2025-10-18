package com.jetski.shared.authorization;

import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Interceptor ABAC (Attribute-Based Access Control) para autorização.
 *
 * Intercepta todos os requests HTTP e consulta o OPA para decisão de autorização
 * baseada em atributos de:
 * - Usuário (ID, tenant, role, email)
 * - Recurso (ID, tenant, type)
 * - Contexto (timestamp, IP, device, environment)
 * - Operação (ação sendo realizada)
 *
 * Substitui @PreAuthorize annotations para autorização centralizada via OPA.
 *
 * @author Jetski Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ABACAuthorizationInterceptor implements HandlerInterceptor {

    private final OPAAuthorizationService opaService;
    private final ActionExtractor actionExtractor;

    @Value("${spring.profiles.active:development}")
    private String environment;

    /**
     * Pre-handle: executa ANTES do controller.
     *
     * Extrai contexto completo do request e consulta OPA para decisão.
     */
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {

        // Obtém autenticação do contexto Spring Security
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Se não autenticado, deixa o SecurityConfig lidar (401)
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("Request não autenticado, delegando para SecurityConfig");
            return true;
        }

        // Extrai action do request
        String action = actionExtractor.extractAction(request);

        // Endpoints públicos ou de infra não requerem autorização ABAC
        if (isPublicEndpoint(action)) {
            log.debug("Public endpoint, skipping ABAC: {}", action);
            return true;
        }

        // Constrói OPAInput completo
        OPAInput input = buildOPAInput(request, authentication, action);

        // Consulta OPA para decisão
        OPADecision decision = opaService.authorize(input);

        // Log da decisão
        logDecision(action, decision);

        // Se negado, lança exceção
        if (!decision.isAllowed()) {
            if (decision.requiresApproval()) {
                throw new AccessDeniedException(String.format(
                    "Ação '%s' requer aprovação de: %s",
                    action,
                    decision.getAprovadorRequerido()
                ));
            }
            throw new AccessDeniedException(String.format(
                "Acesso negado para ação '%s': tenant_valid=%s",
                action,
                decision.isTenantValid()
            ));
        }

        // Autorizado - permite request prosseguir
        return true;
    }

    /**
     * Constrói OPAInput completo com todos os atributos.
     */
    private OPAInput buildOPAInput(HttpServletRequest request,
                                   Authentication authentication,
                                   String action) {
        return OPAInput.builder()
            .action(action)
            .user(buildUserContext(authentication))
            .resource(buildResourceContext(request))
            .context(buildContextAttributes(request))
            .build();
    }

    /**
     * Constrói UserContext do Authentication (JWT).
     */
    private OPAInput.UserContext buildUserContext(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();

        // Extrai primeira role (simplificação - em produção pode ter múltiplas)
        String role = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(auth -> auth.startsWith("ROLE_"))
            .map(auth -> auth.substring(5))  // Remove "ROLE_" prefix
            .findFirst()
            .orElse("NONE");

        // Extrai todas as roles
        List<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(auth -> auth.startsWith("ROLE_"))
            .map(auth -> auth.substring(5))
            .toList();

        UUID tenantId = TenantContext.getTenantId();

        return OPAInput.UserContext.builder()
            .id(jwt.getSubject())
            .tenant_id(tenantId != null ? tenantId.toString() : null)
            .role(role)
            .roles(roles)
            .email(jwt.getClaimAsString("email"))
            .build();
    }

    /**
     * Constrói ResourceContext do request.
     */
    private OPAInput.ResourceContext buildResourceContext(HttpServletRequest request) {
        String resourceId = actionExtractor.extractResourceId(request);
        UUID tenantId = TenantContext.getTenantId();

        return OPAInput.ResourceContext.builder()
            .id(resourceId)
            .tenant_id(tenantId != null ? tenantId.toString() : null)
            .build();
    }

    /**
     * Constrói ContextAttributes do request.
     */
    private OPAInput.ContextAttributes buildContextAttributes(HttpServletRequest request) {
        return OPAInput.ContextAttributes.builder()
            .timestamp(Instant.now().toString())
            .ip(extractClientIP(request))
            .device(detectDevice(request))
            .user_agent(request.getHeader("User-Agent"))
            .environment(environment)
            .build();
    }

    /**
     * Extrai IP do cliente (considera X-Forwarded-For para proxies).
     */
    private String extractClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Detecta tipo de device baseado no User-Agent.
     */
    private String detectDevice(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return "unknown";
        }

        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "mobile";
        }
        if (ua.contains("postman") || ua.contains("insomnia") || ua.contains("curl")) {
            return "api";
        }
        return "web";
    }

    /**
     * Verifica se endpoint é público (não requer ABAC).
     */
    private boolean isPublicEndpoint(String action) {
        return action.startsWith("auth-test:public") ||
               action.startsWith("actuator:") ||
               action.startsWith("health:") ||
               action.startsWith("metrics:");
    }

    /**
     * Log estruturado da decisão de autorização.
     */
    private void logDecision(String action, OPADecision decision) {
        if (decision.isAllowed()) {
            log.info("ABAC ALLOW: action={}, tenant_valid={}, user={}, tenant={}",
                action,
                decision.isTenantValid(),
                SecurityContextHolder.getContext().getAuthentication().getName(),
                TenantContext.getTenantId());
        } else {
            log.warn("ABAC DENY: action={}, tenant_valid={}, requires_approval={}, user={}, tenant={}",
                action,
                decision.isTenantValid(),
                decision.requiresApproval(),
                SecurityContextHolder.getContext().getAuthentication().getName(),
                TenantContext.getTenantId());
        }
    }
}
