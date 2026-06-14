/**
 * Módulo de Reservas (eventos de domínio).
 *
 * <p>Publica eventos de reserva (criada/confirmada/cancelada) consumidos pelo
 * módulo audit.
 *
 * <p>Marcado como OPEN: expõe os eventos de domínio para consumo cross-module.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Reservations",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package com.jetski.reservas;
