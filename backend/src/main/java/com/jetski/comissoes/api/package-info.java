/**
 * Public API for Comissao (Commission) operations
 *
 * <p>This package contains services, controllers, and DTOs that are exposed to other modules.
 * It provides a clean, stable API for commission-related operations without exposing
 * internal implementation details or repositories.</p>
 *
 * <p><strong>Exposed Services:</strong>
 * <ul>
 *   <li>{@link com.jetski.comissoes.api.ComissaoQueryService} - Query service for commissions</li>
 *   <li>{@link com.jetski.comissoes.api.ComissaoController} - REST controller for commission operations</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@org.springframework.modulith.NamedInterface("api")
package com.jetski.comissoes.api;
