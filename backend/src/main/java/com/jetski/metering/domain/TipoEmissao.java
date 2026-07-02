package com.jetski.metering.domain;

/** Tipos de fato de uso contabilizados por tenant. */
public enum TipoEmissao {
    /** Emissão consolidada de documentos (EmissaoService.emitir) — cobrável no futuro. */
    DOCUMENTO,
    /** Geração real de GRU na Marinha (PIX ou boleto) — custo real por geração. */
    GRU,
    /** Prévia de documento gerada — não cobrável; sinal antifraude prévias × emissões. */
    PREVIA
}
