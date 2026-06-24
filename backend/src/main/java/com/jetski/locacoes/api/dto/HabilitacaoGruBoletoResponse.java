package com.jetski.locacoes.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resposta da geração do boleto da GRU (PDF). {@code downloadUrl} = URL presignada do PDF.
 * Em falha, {@code sucesso=false} + {@code erroCodigo} → backoffice cai no fluxo manual.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HabilitacaoGruBoletoResponse {

    private boolean sucesso;
    private boolean reaproveitada;
    private String downloadUrl;
    private String idMarinha;
    private String erroCodigo;
    private String erroMensagem;
}
