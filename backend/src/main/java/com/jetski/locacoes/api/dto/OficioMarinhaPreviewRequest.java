package com.jetski.locacoes.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Valores da tela de configurações usados na pré-visualização do ofício à Capitania.
 *
 * <p>A tela manda o que está DIGITADO (mesmo antes de salvar) para o operador ver o
 * efeito de cada campo. Campo {@code null} = usar o valor gravado do tenant; string
 * em branco = o operador limpou o campo (tratado como ausente).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OficioMarinhaPreviewRequest {
    private String razaoSocial;
    private String marinhaEmail;
    private String emailRemetente;
    private String responsavelNome;
    private String telefone;
    private String emailOficial;
}
