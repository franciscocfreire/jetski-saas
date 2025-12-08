package com.jetski.shared.internal;

import com.jetski.shared.exception.InvalidTenantException;
import com.jetski.shared.observability.BusinessMetrics;
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

    private final TenantAccessValidator tenantAccessValidator;
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

        // Store resolved PostgreSQL usuario.id (NOT Keycloak UUID!)
        if (accessInfo.getUsuarioId() != null) {
            TenantContext.setUsuarioId(accessInfo.getUsuarioId());
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
               normalizedPath.equals("/v1/auth/complete-activation") ||  // Account activation (Option 2: temp password)
               normalizedPath.equals("/v1/auth/magic-activate") ||  // Account activation (Magic link JWT - one-click UX)
               normalizedPath.startsWith("/v1/signup/") ||  // Self-service tenant signup (public)
               normalizedPath.startsWith("/v1/public/") ||  // Public marketplace API (no auth, no tenant)
               normalizedPath.startsWith("/v1/storage/local/") ||  // Local storage endpoints (simulated presigned URLs)
               normalizedPath.startsWith("/v1/test/");  // E2E test utilities (local/test/dev profile only)
    }
}
