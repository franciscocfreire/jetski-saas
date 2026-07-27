package com.jetski.plataforma.event;

import java.util.UUID;

/**
 * Mudança da política de 2FA do console. Evento de plataforma — sem tenant.
 *
 * @param actor  operador que mudou
 * @param antes  exigia 2FA a cada login antes da mudança
 * @param depois passa a exigir
 */
public record SegurancaConsoleAlteradaEvent(UUID actor, boolean antes, boolean depois) {}
