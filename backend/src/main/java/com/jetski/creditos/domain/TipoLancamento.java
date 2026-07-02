package com.jetski.creditos.domain;

/** Tipos de movimento no ledger de créditos. */
public enum TipoLancamento {
    /** Crédito inicial concedido na aprovação do tenant (1 por tenant, idempotente). */
    ADESAO,
    /** Lançamento manual do super admin (positivo ou negativo; motivo obrigatório). */
    AJUSTE,
    /** Débito de 1 crédito por documento emitido à Marinha (1 por documento). */
    CONSUMO,
    /** Devolução de crédito (ex.: emissão invalidada) — sempre via admin. */
    ESTORNO
}
