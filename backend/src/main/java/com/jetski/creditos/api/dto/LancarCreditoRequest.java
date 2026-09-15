package com.jetski.creditos.api.dto;

import java.util.UUID;

/**
 * Lançamento manual de créditos pelo super admin.
 *
 * @param quantidade ± no AJUSTE (negativo vira ESTORNO); só positiva na CORTESIA
 * @param motivo     obrigatório (auditado)
 * @param tipo       AJUSTE (padrão, quando ausente) ou CORTESIA (V077)
 * @param condicaoId só na CORTESIA: condição comercial que a motivou (opcional)
 */
public record LancarCreditoRequest(
        int quantidade,
        String motivo,
        String tipo,
        UUID condicaoId
) {}
