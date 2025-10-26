package com.jetski.shared.observability;

/**
 * MDC (Mapped Diagnostic Context) Keys
 *
 * Centralized constants for all MDC keys used across the application.
 * These keys are automatically included in all log entries when using
 * Logback with LogstashEncoder.
 *
 * MDC provides a way to enrich log messages with contextual information
 * that is automatically available to all log statements within the same
 * thread (typically a single HTTP request).
 *
 * @author Jetski Team
 * @since 0.7.5
 */
public final class MDCKeys {

    private MDCKeys() {
        // Utility class, no instantiation
    }

    /**
     * Unique identifier for the current HTTP request
     * Format: UUID (e.g., "abc123-def456-ghi789")
     * Used for: Request correlation across logs, distributed tracing
     */
    public static final String TRACE_ID = "trace_id";

    /**
     * Tenant identifier extracted from X-Tenant-Id header
     * Format: UUID
     * Used for: Tenant-specific log filtering, multi-tenant observability
     */
    public static final String TENANT_ID = "tenant_id";

    /**
     * User identifier (populated after authentication)
     * Format: UUID
     * Used for: User activity tracking, audit trail
     */
    public static final String USER_ID = "user_id";

    /**
     * Client IP address (considering X-Forwarded-For proxy headers)
     * Format: IPv4 or IPv6 address
     * Used for: Security monitoring, rate limiting, geolocation
     */
    public static final String REMOTE_IP = "remote_ip";

    /**
     * HTTP method (GET, POST, PUT, DELETE, PATCH, etc.)
     * Used for: Request type filtering, API usage analytics
     */
    public static final String REQUEST_METHOD = "request_method";

    /**
     * Request URI path (e.g., "/api/v1/tenants/{id}/locacoes")
     * Used for: Endpoint-specific log filtering, performance analysis
     */
    public static final String REQUEST_URI = "request_uri";
}
