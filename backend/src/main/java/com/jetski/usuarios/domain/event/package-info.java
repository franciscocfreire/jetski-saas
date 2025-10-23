/**
 * Domain Events - User Module
 *
 * <p>This package contains domain events that are published by the usuarios module
 * and can be consumed by other modules.
 *
 * <p><strong>Event-Driven Architecture:</strong><br>
 * These events follow Spring Modulith's ApplicationModuleListener pattern for
 * loose coupling between modules. Other modules can subscribe to these events
 * without creating tight dependencies.
 *
 * <p><strong>Public API:</strong><br>
 * This package is explicitly exposed as a public API so other modules (like shared)
 * can listen to these events.
 *
 * @since 0.4.0
 */
@org.springframework.modulith.NamedInterface("events")
package com.jetski.usuarios.domain.event;
