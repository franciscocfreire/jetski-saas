package com.jetski.locacoes.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resposta da geração do boleto da GRU (PDF). O PDF é baixado via GET .../gru/boleto/download
 * (stream autenticado). Em falha, {@code sucesso=false} + {@code erroCodigo} → fluxo manual.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HabilitacaoGruBoletoResponse {

    private boolean sucesso;
    private boolean reaproveitada;
    private String idMarinha;
    private String gruNumero;
    private java.math.BigDecimal gruValor;
    private String erroCodigo;
    private String erroMensagem;
}
