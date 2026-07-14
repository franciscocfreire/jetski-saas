package com.jetski.tenant.api.dto;

import com.jetski.tenant.domain.Capitania;

import java.util.UUID;

/**
 * Capitania do catálogo de plataforma (V047).
 *
 * @author Jetski Team
 */
public record CapitaniaResponse(
    UUID id,
    String codigo,
    String nome,
    String uf,
    String emailOficial,
    Boolean ativa
) {
    public static CapitaniaResponse of(Capitania c) {
        return new CapitaniaResponse(
            c.getId(), c.getCodigo(), c.getNome(), c.getUf(), c.getEmailOficial(), c.getAtiva());
    }
}
