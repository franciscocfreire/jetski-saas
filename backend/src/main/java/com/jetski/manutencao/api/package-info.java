/**
 * Public API for Manutencao (Maintenance) operations.
 *
 * <p>This package contains services, controllers, and DTOs that are exposed to other modules.
 * It provides a clean, stable API for maintenance-related operations without exposing
 * internal implementation details or repositories.</p>
 *
 * <h2>Exposed Services</h2>
 * <ul>
 *   <li>{@link com.jetski.manutencao.api.ManutencaoPublicService} - Public service for creating maintenance orders</li>
 *   <li>{@link com.jetski.manutencao.api.OSManutencaoController} - REST controller for maintenance operations</li>
 * </ul>
 *
 * <h2>Usage Guidelines</h2>
 * <ul>
 *   <li>Other modules SHOULD use {@link com.jetski.manutencao.api.ManutencaoPublicService} for programmatic access</li>
 *   <li>External clients (mobile/web) SHOULD use REST endpoints via {@link com.jetski.manutencao.api.OSManutencaoController}</li>
 *   <li>DO NOT access {@code com.jetski.manutencao.internal.*} directly - these are private implementation</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <p>This API layer follows the Hexagonal Architecture pattern:
 * <ul>
 *   <li><strong>api</strong> - Public interface (this package)</li>
 *   <li><strong>domain</strong> - Domain entities (exposed separately via @NamedInterface)</li>
 *   <li><strong>internal</strong> - Private implementation (services, repositories)</li>
 * </ul>
 *
 * @since 0.9.0
 * @author Jetski Team
 */
@org.springframework.modulith.NamedInterface("api")
package com.jetski.manutencao.api;
