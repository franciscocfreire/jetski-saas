package com.jetski.locacoes.internal.gru;

/**
 * Falha na geração automática da GRU. O código orienta o fallback manual.
 */
public class GruException extends RuntimeException {

    public enum Codigo {
        MARINHA_INDISPONIVEL,  // passos 1-3 (site da Marinha)
        BRIDGE_FALHOU,         // passos 4-5 (ponte Marinha → PagTesouro)
        PAGTESOURO_FALHOU,     // passos 6-7 (PagTesouro/PIX)
        DADOS_INVALIDOS        // contribuinte incompleto antes de chamar
    }

    private final Codigo codigo;

    public GruException(Codigo codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public GruException(Codigo codigo, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.codigo = codigo;
    }

    public Codigo getCodigo() {
        return codigo;
    }
}
