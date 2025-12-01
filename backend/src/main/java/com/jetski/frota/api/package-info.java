/**
 * Public API for Fleet Management module.
 *
 * <p>Exposes REST controllers and DTOs for:
 * <ul>
 *   <li>Fleet dashboard with KPIs and operational metrics</li>
 *   <li>Advanced jetski search with filters</li>
 * </ul>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /api/v1/frota/dashboard} - Get complete fleet dashboard</li>
 * </ul>
 *
 * @since 0.9.0
 */
@org.springframework.modulith.NamedInterface("api")
package com.jetski.frota.api;
