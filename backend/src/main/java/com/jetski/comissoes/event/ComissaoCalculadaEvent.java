package com.jetski.comissoes.event;

import java.util.UUID;

/**
 * Evento publicado quando uma comissão é calculada acima do preço base.
 *
 * <p>Consumido pelo módulo {@code bonus} para verificar/criar bônus do vendedor,
 * desacoplando comissoes de bonus (evita ciclo comissoes ↔ bonus).
 */
public record ComissaoCalculadaEvent(UUID tenantId, UUID vendedorId) {
}
