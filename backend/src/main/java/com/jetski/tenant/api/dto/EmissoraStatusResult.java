package com.jetski.tenant.api.dto;

import java.util.UUID;

/**
 * Resultado da habilitação/desabilitação de emissora pelo super admin (V047).
 *
 * @author Jetski Team
 */
public record EmissoraStatusResult(
    UUID tenantId,
    boolean emissoraHabilitada,
    String mensagem
) {
}
