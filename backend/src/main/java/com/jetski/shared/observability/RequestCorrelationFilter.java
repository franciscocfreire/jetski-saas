package com.jetski.shared.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Request Correlation Filter
 *
 * Generates a unique trace_id for each HTTP request and populates MDC
 * (Mapped Diagnostic Context) with contextual information.
 *
 * This filter enables request correlation across the entire stack:
 * - All log entries for a single request share the same trace_id
 * - The trace_id is returned to the client via X-Trace-Id response header
 * - Clients can include X-Trace-Id in subsequent requests for correlation
 *
 * MDC keys populated:
 * - trace_id: Unique identifier for this request (UUID)
 * - tenant_id: Extracted from X-Tenant-Id header
 * - remote_ip: Client IP (considering X-Forwarded-For proxy)
 * - request_method: HTTP method (GET, POST, etc.)
 * - request_uri: Request URI path
 *
 * Order: 1 (execute before TenantFilter which is Order 2)
 *
 * @author Jetski Team
 * @since 0.7.5
 */
@Component
@Order(1)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        // Generate or extract trace_id
        String traceId = extractOrGenerateTraceId(request);

        try {
            // Populate MDC with contextual information
            MDC.put(MDCKeys.TRACE_ID, traceId);
            MDC.put(MDCKeys.TENANT_ID, request.getHeader(TENANT_ID_HEADER));
            MDC.put(MDCKeys.REMOTE_IP, extractClientIP(request));
            MDC.put(MDCKeys.REQUEST_METHOD, request.getMethod());
            MDC.put(MDCKeys.REQUEST_URI, request.getRequestURI());

            // Add trace_id to response header for client correlation
            response.addHeader(TRACE_ID_HEADER, traceId);

            // Log request start (DEBUG level to avoid noise)
            if (log.isDebugEnabled()) {
                log.debug("Request started: method={}, uri={}, remoteIP={}",
                    request.getMethod(), request.getRequestURI(), extractClientIP(request));
            }

            // Continue filter chain
            filterChain.doFilter(request, response);

        } finally {
            // Always clear MDC to prevent memory leaks in thread pools
            MDC.clear();
        }
    }

    /**
     * Extract trace_id from request header or generate new UUID
     *
     * If client sends X-Trace-Id header (e.g., for request retry correlation),
     * we reuse it. Otherwise, generate a new UUID.
     */
    private String extractOrGenerateTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_HEADER);

        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }

        return traceId;
    }

    /**
     * Extract client IP considering X-Forwarded-For header
     *
     * When behind a proxy/load balancer, the real client IP is in X-Forwarded-For.
     * Format: "clientIP, proxy1IP, proxy2IP"
     * We extract the first IP (original client).
     *
     * Fallback to request.getRemoteAddr() if no proxy headers present.
     */
    private String extractClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader(FORWARDED_FOR_HEADER);

        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            // X-Forwarded-For can be: "client, proxy1, proxy2"
            // We want the first (original client)
            String[] ips = xForwardedFor.split(",");
            return ips[0].trim();
        }

        // No proxy, use direct remote address
        return request.getRemoteAddr();
    }

    /**
     * This filter applies to all requests (no exceptions)
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false;
    }
}
