package com.jetski.locacoes.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resposta da verificação de pagamento do PIX da GRU (PagTesouro pix-stn/sonda).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HabilitacaoGruPagamentoResponse {

    private boolean pago;
    private String situacao;               // CONCLUIDO | PENDENTE | SEM_SESSAO | ERRO
    private boolean comprovanteDisponivel; // PDF do comprovante pronto p/ download
}
