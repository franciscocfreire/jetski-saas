package com.jetski.tenant.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Dados do perfil de emissão declarados pelo tenant (V047):
 * capitania + registro EAMA. A habilitação em si é do super admin.
 *
 * @author Jetski Team
 */
public record EmissoraConfigRequest(
    UUID capitaniaId,
    String eamaRegistro,
    LocalDate eamaRegistroValidade
) {
}
