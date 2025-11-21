/**
 * Internal services for Combustivel module.
 *
 * <p>This package contains internal services that are exposed to specific modules
 * that need to calculate fuel costs (e.g., locacoes module needs to calculate
 * fuel cost during checkout).
 *
 * <p><strong>Exposed Services:</strong>
 * <ul>
 *   <li>{@link com.jetski.combustivel.internal.FuelPolicyService} - Fuel cost calculation service</li>
 * </ul>
 *
 * <p><strong>Note:</strong> While this package is marked as a named interface to allow
 * controlled access from allowed modules (locacoes), this creates a necessary bidirectional
 * dependency which is acceptable for tightly coupled business logic.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@org.springframework.modulith.NamedInterface("internal")
package com.jetski.combustivel.internal;
