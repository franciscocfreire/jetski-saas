/**
 * Public API for Locacao (Rental) operations
 *
 * <p>This package contains services, controllers, and DTOs that are exposed to other modules.
 * It provides a clean, stable API for rental-related operations without exposing
 * internal implementation details or repositories.</p>
 *
 * <p><strong>Exposed Services:</strong>
 * <ul>
 *   <li>{@link com.jetski.locacoes.api.LocacaoQueryService} - Query service for rentals</li>
 *   <li>{@link com.jetski.locacoes.api.JetskiPublicService} - Public service for jetski operations</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@org.springframework.modulith.NamedInterface("api")
package com.jetski.locacoes.api;
