package com.jetski.combustivel.domain;

/**
 * Tipo de aplicação da política de combustível.
 *
 * Define a hierarquia de busca de políticas (RN03):
 * 1. JETSKI: política específica para um jetski individual (maior prioridade)
 * 2. MODELO: política específica para um modelo de jetski
 * 3. GLOBAL: política padrão para todo o tenant (fallback)
 *
 * A busca segue a hierarquia e retorna a primeira política ativa encontrada.
 */
public enum FuelPolicyType {

    /**
     * Política global do tenant (fallback).
     * Aplicável a todos os jetskis que não possuem política específica.
     */
    GLOBAL,

    /**
     * Política específica para um modelo de jetski.
     * Referencia um modelo_id.
     */
    MODELO,

    /**
     * Política específica para um jetski individual (maior prioridade).
     * Referencia um jetski_id.
     */
    JETSKI;

    public boolean isGlobal() {
        return this == GLOBAL;
    }

    public boolean isModelo() {
        return this == MODELO;
    }

    public boolean isJetski() {
        return this == JETSKI;
    }
}
