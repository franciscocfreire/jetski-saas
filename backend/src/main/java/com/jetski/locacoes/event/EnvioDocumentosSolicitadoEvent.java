package com.jetski.locacoes.event;

import java.util.UUID;

/**
 * Domain Event: os documentos de uma reserva foram emitidos e commitados, e os
 * e-mails (ofício à Capitania + via do cliente) ainda precisam sair.
 *
 * <p>Consumido dentro do próprio módulo pelo worker de envio. Carrega o
 * {@code tenantId} porque a thread assíncrona nasce sem {@code TenantContext} e a
 * RLS depende dele.
 */
public record EnvioDocumentosSolicitadoEvent(
    UUID tenantId,
    UUID documentoId,
    UUID reservaId,
    UUID usuarioId
) {}
