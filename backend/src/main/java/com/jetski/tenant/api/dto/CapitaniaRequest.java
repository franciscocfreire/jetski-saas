package com.jetski.tenant.api.dto;

/**
 * Criação/edição de capitania pelo super admin (V047).
 *
 * @author Jetski Team
 */
public record CapitaniaRequest(
    String codigo,
    String nome,
    String uf,
    String emailOficial,
    Boolean ativa
) {
}
