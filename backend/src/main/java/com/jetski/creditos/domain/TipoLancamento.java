package com.jetski.creditos.domain;

/** Tipos de movimento no ledger de créditos. */
public enum TipoLancamento {
    /** Crédito inicial concedido na aprovação do tenant (1 por tenant, idempotente). */
    ADESAO,
    /** Correção manual do super admin (positiva; a negativa vira ESTORNO; motivo obrigatório). */
    AJUSTE,
    /**
     * Concessão gratuita do super admin (V077): sempre positiva, motivo obrigatório, com
     * vínculo opcional à condição comercial que a motivou (ex.: piloto). Separada de AJUSTE
     * para a plataforma saber quanto crédito deu × vendeu.
     */
    CORTESIA,
    /** Débito de 1 crédito por documento emitido à Marinha (1 por documento). */
    CONSUMO,
    /** Devolução de crédito (ex.: emissão invalidada) — sempre via admin. */
    ESTORNO
}
