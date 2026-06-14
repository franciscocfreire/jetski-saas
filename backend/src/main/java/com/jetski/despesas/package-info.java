/**
 * Module for Operational Expenses (Despesas Operacionais).
 *
 * <p>Manages daily operational expenses not linked to specific rentals,
 * such as employee daily wages, meals, cleaning, etc.</p>
 *
 * @since 0.9.0
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Despesas Operacionais",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN,
    allowedDependencies = {
        "shared::security",
        "shared::exception",
        "usuarios::api"
    }
)
package com.jetski.despesas;
