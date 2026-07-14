package com.jetski.tenant.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Perfil de emissão do tenant (V047): capitania, registro EAMA e o
 * estado da habilitação validada pelo super admin (read-only aqui).
 *
 * @author Jetski Team
 */
public record EmissoraConfigResponse(
    UUID capitaniaId,
    String capitaniaCodigo,
    String capitaniaNome,
    String eamaRegistro,
    LocalDate eamaRegistroValidade,
    boolean emissoraHabilitada
) {
}
