package com.jetski.security;

import com.jetski.exception.InvalidTenantException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
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
 * - Checks if tenant ID matches JWT claim 'tenant_id'
 * - Throws InvalidTenantException if mismatch
 *
 * NOTE: This filter is NOT a @Component - it's added programmatically
 * to the protected SecurityFilterChain in SecurityConfig.
 *
 * @author Jetski Team
 * @since 0.1.0
 * @see TenantContext
 * @see InvalidTenantException
 */
@Slf4j
public class TenantFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER_NAME = "X-Tenant-Id";

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

            // 3. Validate against JWT (if authenticated)
            validateAgainstJwt(tenantId, request);

            // 4. Store in context
            TenantContext.setTenantId(tenantId);

            log.debug("Tenant context set successfully: tenantId={}, path={}, method={}",
                    tenantId, requestPath, request.getMethod());

            // 5. Continue filter chain
            filterChain.doFilter(request, response);

        } finally {
            // 6. ALWAYS clear context to prevent memory leaks
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
     * Validate tenant ID against JWT claim (if user is authenticated)
     *
     * @param tenantId tenant ID from header/subdomain
     * @param request HTTP request
     * @throws InvalidTenantException if mismatch
     */
    private void validateAgainstJwt(UUID tenantId, HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Skip validation if not authenticated yet
        if (auth == null || !auth.isAuthenticated() ||
            "anonymousUser".equals(auth.getPrincipal())) {
            log.debug("Skipping JWT validation - user not authenticated");
            return;
        }

        // Extract tenant_id from JWT
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String jwtTenantId = jwt.getClaimAsString("tenant_id");

            if (jwtTenantId == null) {
                log.warn("JWT does not contain tenant_id claim");
                return;  // Allow for now, maybe user is super admin
            }

            // Validate match
            if (!tenantId.toString().equals(jwtTenantId)) {
                log.error("Tenant ID mismatch: header={}, JWT={}", tenantId, jwtTenantId);
                throw InvalidTenantException.mismatch(tenantId.toString(), jwtTenantId);
            }

            log.debug("Tenant ID validated successfully against JWT: {}", tenantId);
        }
    }

    /**
     * Check if endpoint is public (no tenant required)
     *
     * @param path request path
     * @return true if public endpoint
     */
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/actuator/") ||
               path.startsWith("/api/v3/api-docs") ||
               path.startsWith("/api/swagger-ui") ||
               path.equals("/api/health") ||
               path.equals("/api/") ||
               path.startsWith("/api/v1/auth-test/public");  // Test endpoint
    }
}
