package com.jetski.locacoes.internal.gru;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Status do pagamento do PIX (PagTesouro pix-stn/sonda). Quando {@code pago=true},
 * os demais campos trazem os dados do comprovante.
 */
public record GruPagamentoStatus(
    boolean pago,
    String situacao,            // "CONCLUIDO" quando pago; "PENDENTE"/"SEM_SESSAO" caso contrário
    Instant dataPagamento,
    String idPagamento,
    String numeroReferencia,
    String descricao,
    BigDecimal valor,
    String refTran,             // ID da transação no prestador (E2E PIX)
    String nomeContribuinte,
    String cpfContribuinte,
    String formaPagamento
) {
    public static GruPagamentoStatus naoPago(String situacao) {
        return new GruPagamentoStatus(false, situacao, null, null, null, null, null, null, null, null, null);
    }
}
