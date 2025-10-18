/**
 * Security API - Public interfaces and DTOs for authentication and authorization.
 *
 * <p>This package defines the public security contract of the shared module:
 * <ul>
 *   <li>{@link TenantAccessValidator} - Interface for tenant access validation</li>
 *   <li>{@link TenantAccessInfo} - DTO for access validation results</li>
 *   <li>{@link TenantContext} - Thread-local tenant context holder</li>
 * </ul>
 *
 * <p><strong>Module Architecture:</strong><br>
 * This is a named interface of the 'shared' module. Other modules can depend on
 * these types without breaking modularity rules.
 *
 * @since 0.2.0
 */
@org.springframework.modulith.NamedInterface("security")
package com.jetski.shared.security;
