package com.jetski.locacoes.internal.gru;

/**
 * Dados do contribuinte para emissão da GRU (montados a partir do Cliente).
 */
public record GruContribuinte(
    String cpf,
    String nome,
    String telefone,
    String email,
    String sexo,          // "M" | "F" | ""
    String cep,
    String logradouro,
    String numero,
    String complemento,
    String bairro,
    String municipio,
    String uf
) {}
