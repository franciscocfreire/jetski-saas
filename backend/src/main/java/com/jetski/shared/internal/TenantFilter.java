package com.jetski.shared.internal;

import com.jetski.shared.exception.InvalidTenantException;
import com.jetski.shared.observability.BusinessMetrics;
import com.jetski.shared.security.PlatformAccessInfo;
import com.jetski.shared.security.SessaoSuporte;
import com.jetski.shared.security.SessaoSuporteValidator;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.security.TenantAccessValidator;
import com.jetski.shared.security.TenantAccessInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter to extract and validate tenant ID from HTTP requests
 *
 * This filter is added to the PROTECTED SecurityFilterChain only,
 * AFTER OAuth2 authentication, to ensure tenant context is available.
 *
 * Extraction priority:
 * 1. X-Tenant-Id header
 * 2. Subdomain (e.g., acme.jetski.com → acme)
 *
 * Validation (if user is authenticated):
 * - Validates tenant access via database (TenantAccessService)
 * - Checks if user is a member of the tenant OR has unrestricted access
 * - Stores roles in TenantContext for @PreAuthorize
 * - Throws AccessDeniedException if access is denied
 *
 * @author Jetski Team
 * @since 0.2.0 - Updated to use database validation instead of JWT claim
 * @see TenantContext
 * @see TenantAccessService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER_NAME = "X-Tenant-Id";
    /** Cookie do handoff console→backoffice (ver SessaoSuporteService). */
    public static final String COOKIE_SUPORTE = "mj_support";

    private final TenantAccessValidator tenantAccessValidator;
    private final SessaoSuporteValidator sessaoSuporteValidator;
    private final BusinessMetrics businessMetrics;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // Skip tenant validation for public endpoints
            String requestPath = request.getRequestURI();
            if (isPublicEndpoint(requestPath)) {
                log.debug("Skipping tenant validation for public endpoint: {}", requestPath);
                filterChain.doFilter(request, response);
                return;
            }

            // Escopo do cliente final (/v1/customers/**): token sem X-Tenant-Id.
            // QUALQUER usuário autenticado assume a persona CLIENTE aqui — staff
            // de um tenant também pode ser cliente da plataforma. Os papéis de
            // staff NÃO entram no contexto (persona única por escopo); cada
            // serviço customer-scoped resolve os vínculos pelo sub e seta a RLS
            // por tenant internamente (set_config transaction-local).
            if (isCustomerEndpoint(requestPath)) {
                Authentication customerAuth = SecurityContextHolder.getContext().getAuthentication();
                if (customerAuth != null && customerAuth.isAuthenticated()) {
                    TenantContext.setUserRoles(java.util.List.of("CLIENTE"));
                }
                filterChain.doFilter(request, response);
                return;
            }

            // SESSÃO DE SUPORTE: operador de plataforma operando UMA empresa, com motivo,
            // prazo e trilha. A empresa vem da SESSÃO, não de um header que o cliente
            // escolhe — o operador não troca de alvo sem abrir outra sessão.
            SessaoSuporte suporte = resolverSessaoSuporte(request);
            if (suporte != null) {
                TenantContext.setSessaoSuporte(suporte);
                TenantContext.setTenantId(suporte.tenantId());
                TenantContext.setUsuarioId(suporte.operadorId());
                aplicarPapeisDePlataforma(request);
                concederAutoridadeDeEmpresa();
                businessMetrics.recordTenantContextSwitch(suporte.tenantId().toString());
                filterChain.doFilter(request, response);
                return;
            }

            // Escopo de PLATAFORMA (/v1/platform/**): identidade global, sem tenant
            // obrigatório. O console (admin.*) não tem "empresa corrente" — o alvo, quando
            // existe, vem no path (/v1/platform/tenants/{id}/...). O gate de papel fica no
            // PlatformScopeInterceptor; aqui só populamos o contexto.
            if (isPlatformEndpoint(requestPath)) {
                applyPlatformContext(request);
                filterChain.doFilter(request, response);
                return;
            }

            // 1. Extract tenant ID from request
            String tenantIdStr = extractTenantId(request);

            // 2. Validate format (must be valid UUID)
            UUID tenantId = parseTenantId(tenantIdStr);

            // 3. Store tenant in context EARLY (before any DB queries)
            // This ensures RLS works for access validation queries AND business queries
            TenantContext.setTenantId(tenantId);

            // 4. Extract user identity from JWT (provider + providerUserId)
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() &&
                !auth.getPrincipal().equals("anonymousUser") &&
                auth.getPrincipal() instanceof Jwt jwt) {

                // Extract identity provider info from JWT
                String provider = JwtAuthenticationConverter.extractProvider(jwt);
                String providerUserId = JwtAuthenticationConverter.extractProviderUserId(jwt);

                // 5. Validate access via database (resolves internal usuario_id via mapping)
                validateAccessViaDatabase(provider, providerUserId, tenantId);
            }

            // 6. Record tenant context switch metric
            businessMetrics.recordTenantContextSwitch(tenantId.toString());

            log.debug("Tenant context set successfully: tenantId={}, path={}, method={}",
                    tenantId, requestPath, request.getMethod());

            // 7. Continue filter chain
            filterChain.doFilter(request, response);

        } finally {
            // 8. ALWAYS clear context to prevent memory leaks
            TenantContext.clear();
        }
    }

    /**
     * Extract tenant ID from request
     *
     * Priority:
     * 1. X-Tenant-Id header
     * 2. Subdomain (first part of hostname)
     *
     * @param request HTTP request
     * @return tenant ID as string
     * @throws InvalidTenantException if not found
     */
    private String extractTenantId(HttpServletRequest request) {
        // Priority 1: Header X-Tenant-Id
        String tenantId = request.getHeader(TENANT_HEADER_NAME);
        if (tenantId != null && !tenantId.isBlank()) {
            log.debug("Tenant ID extracted from header: {}", tenantId);
            return tenantId.trim();
        }

        // Priority 2: Subdomain (e.g., acme.jetski.com → acme)
        String host = request.getServerName();
        if (host != null && host.contains(".")) {
            String subdomain = host.split("\\.")[0];
            // If subdomain is not "www" or "api", use it as tenant slug
            if (!subdomain.equalsIgnoreCase("www") &&
                !subdomain.equalsIgnoreCase("api") &&
                !subdomain.equalsIgnoreCase("localhost")) {
                log.debug("Tenant slug extracted from subdomain: {}", subdomain);
                // Note: In production, you'd need to lookup tenant UUID by slug
                // For now, we expect UUID in header for MVP
            }
        }

        throw InvalidTenantException.missingTenantId();
    }

    /**
     * Parse tenant ID string to UUID
     *
     * @param tenantIdStr tenant ID as string
     * @return UUID
     * @throws InvalidTenantException if format is invalid
     */
    private UUID parseTenantId(String tenantIdStr) {
        try {
            return UUID.fromString(tenantIdStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid tenant ID format: {}", tenantIdStr);
            throw InvalidTenantException.invalidFormat(tenantIdStr);
        }
    }

    /**
     * Validate tenant access via database using identity provider mapping
     *
     * Queries TenantAccessService to check if user can access this tenant.
     * Resolves internal usuario_id from (provider, providerUserId) mapping.
     * Stores roles in TenantContext for @PreAuthorize.
     *
     * @param provider Identity provider name (e.g., 'keycloak', 'google')
     * @param providerUserId External user ID from provider (JWT sub claim)
     * @param tenantId Tenant UUID from header
     * @throws AccessDeniedException if access is denied or mapping not found
     */
    private void validateAccessViaDatabase(String provider, String providerUserId, UUID tenantId) {
        // Resolve internal usuario_id and validate access in one call
        TenantAccessInfo accessInfo = tenantAccessValidator.validateAccess(provider, providerUserId, tenantId);

        if (!accessInfo.isHasAccess()) {
            log.error("Access denied: provider={}, providerUserId={}, tenant={}, reason={}",
                provider, providerUserId, tenantId, accessInfo.getReason());
            throw new AccessDeniedException("No access to tenant: " + tenantId);
        }

        // Store roles and usuarioId in context for @PreAuthorize and controllers
        TenantContext.setUserRoles(accessInfo.getRoles());

        // Store unrestricted flag (super admin) for ABAC → OPA propagation
        TenantContext.setUnrestricted(accessInfo.isUnrestricted());

        // Store resolved PostgreSQL usuario.id (NOT Keycloak UUID!)
        if (accessInfo.getUsuarioId() != null) {
            TenantContext.setUsuarioId(accessInfo.getUsuarioId());
        }

        // Gate de status: tenants não-operacionais (PENDENTE_APROVACAO/SUSPENSO/INATIVO/
        // CANCELADO) bloqueiam operações de usuários normais. Super admin (irrestrito) é isento.
        if (!accessInfo.isUnrestricted()) {
            String status = accessInfo.getTenantStatus();
            if (status != null && !"ATIVO".equals(status) && !"TRIAL".equals(status)) {
                log.warn("Tenant não-operacional: tenant={}, status={}, usuario={}",
                    tenantId, status, accessInfo.getUsuarioId());
                throw new AccessDeniedException("TENANT_" + status);
            }
        }

        log.debug("Access validated: provider={}, providerUserId={}, tenant={}, roles={}, unrestricted={}, usuarioId={}",
            provider, providerUserId, tenantId, accessInfo.getRoles(), accessInfo.isUnrestricted(), accessInfo.getUsuarioId());
    }

    /**
     * Check if endpoint is public (no tenant required)
     *
     * Handles paths both with and without context-path prefix:
     * - Runtime: /api/actuator/health
     * - Tests (MockMvc): /actuator/health
     *
     * @param path request path
     * @return true if public endpoint
     */
    /** Endpoints do cliente final (portal) — autenticados, porém sem tenant no request. */
    private boolean isCustomerEndpoint(String path) {
        String normalizedPath = path.startsWith("/api/") ? path.substring(4) : path;
        return normalizedPath.startsWith("/v1/customers/") || normalizedPath.equals("/v1/customers");
    }

    /**
     * Lê o cookie de suporte e valida a sessão. Cookie ausente/expirado/encerrado devolve
     * null e o request segue como request normal — quem nega é a autorização.
     */
    private SessaoSuporte resolverSessaoSuporte(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie c : request.getCookies()) {
            if (COOKIE_SUPORTE.equals(c.getName())) {
                return sessaoSuporteValidator.validar(c.getValue());
            }
        }
        return null;
    }

    /**
     * Papéis GLOBAIS do operador durante a sessão de suporte.
     *
     * <p>Ele não vira membro da empresa: os papéis continuam sendo os de plataforma, e a
     * autorização do que pode fazer lá dentro é do OPA, que enxerga a sessão (inclusive o
     * somente-leitura).
     */
    private void aplicarPapeisDePlataforma(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return;
        }
        PlatformAccessInfo acesso = tenantAccessValidator.resolvePlatformAccess(
            JwtAuthenticationConverter.extractProvider(jwt),
            JwtAuthenticationConverter.extractProviderUserId(jwt));
        if (acesso != null) {
            TenantContext.setUserRoles(acesso.roles());
            TenantContext.setUnrestricted(acesso.unrestricted());
        }
    }

    /**
     * Dá ao operador, DURANTE a sessão de suporte, a autoridade Spring de empresa que os
     * controllers exigem em {@code @PreAuthorize("hasAnyRole('ADMIN_TENANT', ...)")}.
     *
     * <p>Sem isto o Spring Security barra com 403 ANTES do ABAC, e a sessão de suporte não
     * serve para nada — o operador de plataforma tem papéis {@code PLATFORM_*}, não papéis
     * de empresa. (Era latente: o god mode "funcionava" só para quem também era membro da
     * empresa; um operador sem vínculo nunca operou o backoffice.)
     *
     * <p><strong>O somente-leitura NÃO passa por aqui de propósito.</strong> Os papéis do
     * {@link TenantContext} — que alimentam o OPA — continuam sendo só os de plataforma.
     * Se {@code ADMIN_TENANT} entrasse lá, o {@code rbac_allow} do authorization.rego
     * liberaria escrita e uma sessão de leitura poderia gravar. O @PreAuthorize é um
     * portão grosso; quem decide leitura×escrita é a regra de sessão de suporte no OPA.
     */
    private void concederAutoridadeDeEmpresa() {
        Authentication atual = SecurityContextHolder.getContext().getAuthentication();
        if (atual == null || !atual.isAuthenticated()) {
            return;
        }
        java.util.List<org.springframework.security.core.GrantedAuthority> autoridades =
            new java.util.ArrayList<>(atual.getAuthorities());
        autoridades.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(
            "ROLE_ADMIN_TENANT"));
        SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                atual.getPrincipal(), atual.getCredentials(), autoridades));
    }

    /** Endpoints de plataforma (console) — autenticados, com tenant OPCIONAL. */
    private boolean isPlatformEndpoint(String path) {
        String normalizedPath = path.startsWith("/api/") ? path.substring(4) : path;
        return normalizedPath.startsWith("/v1/platform/")
            || normalizedPath.equals("/v1/platform")
            // /v1/suporte/**: o alvo vem da SESSÃO, nunca de header. Sem cookie (resgate,
            // ou "tem sessão?" respondendo que não) o request segue sem tenant em vez de
            // 400 — exigir X-Tenant-Id aqui fazia o banner falhar antes de existir sessão.
            // O resgate ainda exige estar AUTENTICADO: ele é amarrado a quem abriu, para
            // que um código vazado (URL, Referer, log de proxy) não sirva a outra pessoa.
            || normalizedPath.startsWith("/v1/suporte/");
    }

    /**
     * Popula o contexto para rotas de plataforma.
     *
     * <p>Resolve papéis globais e {@code unrestricted} pelo JWT, SEM exigir vínculo com
     * empresa. O {@code X-Tenant-Id} continua sendo aceito quando enviado (o backoffice
     * atual manda em todas as chamadas de plataforma): nesse caso a RLS aponta para a
     * empresa alvo exatamente como antes. Quando ausente (console), o contexto de tenant
     * fica nulo — a policy da tabela {@code tenant} (V042) libera pelo ramo
     * {@code app.unrestricted}, e os serviços de plataforma continuam escopando empresa a
     * empresa via {@code set_config(..., true)} dentro da transação.
     *
     * <p>Identidade ausente ou não mapeada não lança aqui: o contexto fica sem papéis e o
     * {@code PlatformScopeInterceptor} responde 403.
     */
    private void applyPlatformContext(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return;
        }

        String provider = JwtAuthenticationConverter.extractProvider(jwt);
        String providerUserId = JwtAuthenticationConverter.extractProviderUserId(jwt);
        PlatformAccessInfo access = tenantAccessValidator.resolvePlatformAccess(provider, providerUserId);
        if (access == null) {
            // Contrato diz "nunca null"; se um dia quebrar, degrada FECHADO (403 no
            // PlatformScopeInterceptor) em vez de 500 numa rota de plataforma.
            access = PlatformAccessInfo.none();
        }

        if (access.usuarioId() != null) {
            TenantContext.setUsuarioId(access.usuarioId());
        }
        TenantContext.setUserRoles(access.roles());
        TenantContext.setUnrestricted(access.unrestricted());

        String header = request.getHeader(TENANT_HEADER_NAME);
        if (header != null && !header.isBlank()) {
            TenantContext.setTenantId(parseTenantId(header.trim()));
        }

        log.debug("Platform context: usuario={}, roles={}, unrestricted={}, tenantAlvo={}",
            access.usuarioId(), access.roles(), access.unrestricted(), TenantContext.getTenantId());
    }

    private boolean isPublicEndpoint(String path) {
        // Remove context-path if present for consistent matching
        String normalizedPath = path.startsWith("/api/") ? path.substring(4) : path;

        return normalizedPath.startsWith("/actuator/") ||
               normalizedPath.startsWith("/v3/api-docs") ||
               normalizedPath.startsWith("/swagger-ui") ||
               normalizedPath.equals("/health") ||
               normalizedPath.equals("/") ||
               normalizedPath.startsWith("/v1/auth-test/public") ||  // Test endpoint
               normalizedPath.equals("/v1/user/tenants") ||  // User tenants list (no tenant needed)
               // Perfil self-service (escopo = próprio sub do JWT, sem tenant).
               // equals + "/" evita colidir com futuros /v1/user/me* (ex.: /v1/user/metrics)
               normalizedPath.equals("/v1/user/me") ||
               normalizedPath.startsWith("/v1/user/me/") ||
               normalizedPath.equals("/v1/auth/complete-activation") ||  // Account activation (Option 2: temp password)
               normalizedPath.equals("/v1/auth/magic-activate") ||  // Account activation (Magic link JWT - one-click UX)
               normalizedPath.startsWith("/v1/signup/") ||  // Self-service tenant signup (public)
               normalizedPath.startsWith("/v1/public/") ||  // Public marketplace API (no auth, no tenant)
               normalizedPath.startsWith("/v1/pdf/") ||  // Abertura de PDF por token (público, sem tenant)
               normalizedPath.startsWith("/v1/storage/local/") ||  // Local storage endpoints (simulated presigned URLs)
               normalizedPath.startsWith("/v1/test/");  // E2E test utilities (local/test/dev profile only)
    }
}
