package com.jetski.locacoes.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain Event: identidade do cliente sincronizada do perfil global para o
 * cadastro desta loja (edição self-service no portal).
 *
 * <p>Trilha LGPD minimizada — carrega apenas os NOMES dos campos alterados
 * (ex.: ["rg","naturalidade"]), nunca os valores. Consumido por
 * {@code AuditEventListener}.
 *
 * @param tenantId        loja cujo Cliente foi atualizado
 * @param clienteId       cliente atualizado
 * @param camposAlterados nomes dos campos sobrescritos
 * @param sub             identidade (Keycloak) do cliente que editou
 * @param occurredAt      quando ocorreu
 */
public record ClienteIdentidadeSincronizadaEvent(
    UUID tenantId,
    UUID clienteId,
    List<String> camposAlterados,
    String sub,
    Instant occurredAt
) {
    public static ClienteIdentidadeSincronizadaEvent of(
            UUID tenantId, UUID clienteId, List<String> camposAlterados, String sub) {
        return new ClienteIdentidadeSincronizadaEvent(
            tenantId, clienteId, List.copyOf(camposAlterados), sub, Instant.now());
    }
}
