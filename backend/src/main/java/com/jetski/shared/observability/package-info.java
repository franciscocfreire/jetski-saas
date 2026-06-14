/**
 * Observability infrastructure for the shared module.
 *
 * <p>Contains cross-cutting concerns for tracing, metrics, MDC correlation keys
 * and health indicators. Exposed as a named interface so consumer modules
 * (e.g. {@code audit}) can reuse correlation keys like {@link com.jetski.shared.observability.MDCKeys}.</p>
 *
 * @since 0.10.0
 */
@org.springframework.modulith.NamedInterface("observability")
package com.jetski.shared.observability;
