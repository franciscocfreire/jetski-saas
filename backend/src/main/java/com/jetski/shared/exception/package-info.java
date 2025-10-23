/**
 * Exception API - Public exception classes for business logic validation.
 *
 * <p>This package defines the public exception contract of the shared module:
 * <ul>
 *   <li>{@link BusinessException} - Base exception for business rule violations</li>
 *   <li>{@link InvalidTenantException} - Exception for tenant-related errors</li>
 * </ul>
 *
 * <p><strong>Module Architecture:</strong><br>
 * This is a named interface of the 'shared' module. Other modules can depend on
 * these types without breaking modularity rules.
 *
 * @since 0.2.0
 */
@org.springframework.modulith.NamedInterface("exception")
package com.jetski.shared.exception;
