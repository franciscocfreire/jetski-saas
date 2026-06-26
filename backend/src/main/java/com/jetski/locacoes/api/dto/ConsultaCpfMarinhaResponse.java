package com.jetski.locacoes.api.dto;

/**
 * Resultado da consulta de nome por CPF na Marinha (pré-preenchimento no balcão).
 * {@code nome} é null quando não encontrado ou se a consulta falhou.
 */
public record ConsultaCpfMarinhaResponse(String nome) {}
