/**
 * Domain Events - Locacoes Module
 *
 * <p>This package contains domain events that are published by the locacoes module
 * and can be consumed by other modules.
 *
 * <p><strong>Event-Driven Architecture:</strong><br>
 * These events follow Spring Modulith's ApplicationModuleListener pattern for
 * loose coupling between modules. Other modules can subscribe to these events
 * without creating tight dependencies.
 *
 * <p><strong>Public API:</strong><br>
 * This package is explicitly exposed as a public API so other modules (like frota)
 * can listen to these events for cache invalidation and metrics updates.
 *
 * @since 0.9.0
 */
@org.springframework.modulith.NamedInterface("events")
package com.jetski.locacoes.domain.event;
