package com.jetski.locacoes.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Resposta da geração automática da GRU + PIX.
 * Em falha, {@code sucesso=false} + {@code erroCodigo} → backoffice cai no fluxo manual.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HabilitacaoGruResponse {

    private boolean sucesso;
    private boolean reaproveitada;       // GRU válida já existia (não duplicou)

    private String gruNumero;
    private BigDecimal gruValor;
    private Boolean gruPago;
    private String pixCopiaECola;
    private String pixQrPngBase64;       // só quando recém-gerada
    private Instant pixExpiracao;
    private String idMarinha;

    private String erroCodigo;           // MARINHA_INDISPONIVEL | BRIDGE_FALHOU | PAGTESOURO_FALHOU | DADOS_INVALIDOS
    private String erroMensagem;
}
