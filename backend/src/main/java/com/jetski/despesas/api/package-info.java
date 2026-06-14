/**
 * API module for Operational Expenses (Despesas Operacionais).
 *
 * <p>Contains REST controllers and DTOs for managing daily operational expenses.</p>
 *
 * @since 0.9.0
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Despesas Operacionais - API",
    allowedDependencies = {
        "despesas::domain",
        "despesas::internal",
        "shared::security",
        "shared::exception",
        "usuarios::api"
    }
)
package com.jetski.despesas.api;
