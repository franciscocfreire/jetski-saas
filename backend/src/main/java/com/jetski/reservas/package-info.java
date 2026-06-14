/**
 * Módulo de Reservas (eventos de domínio).
 *
 * <p>Publica eventos de reserva (criada/confirmada/cancelada) consumidos pelo
 * módulo audit via a named interface {@code events}.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Reservations"
)
package com.jetski.reservas;
