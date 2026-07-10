package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.internal.ReservaPixService;

import java.math.BigDecimal;

/**
 * DTO: PIX copia-e-cola da cobrança presencial do balcão. O {@code copiaECola}
 * é o próprio conteúdo do QR Code (renderizado no frontend).
 */
public record ReservaPixResponse(
    String chave,
    String copiaECola,
    BigDecimal valor
) {
    public static ReservaPixResponse from(ReservaPixService.PixCobranca pix) {
        return new ReservaPixResponse(pix.chave(), pix.copiaECola(), pix.valor());
    }
}
