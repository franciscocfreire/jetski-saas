/**
 * Internal services for Locações module.
 *
 * <p>This package contains internal services that are exposed to specific modules
 * that need to interact with jetski operations (e.g., manutencao module needs to
 * update jetski status when creating maintenance orders).
 *
 * <p><strong>Exposed Services:</strong>
 * <ul>
 *   <li>{@link com.jetski.locacoes.internal.JetskiService} - Jetski management service</li>
 * </ul>
 *
 * <p><strong>Note:</strong> While this package is marked as a named interface to allow
 * controlled access from allowed modules (manutencao), prefer using domain events
 * for inter-module communication in future refactorings.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@org.springframework.modulith.NamedInterface("internal")
package com.jetski.locacoes.internal;
