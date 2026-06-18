package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Cadastro/edição de instrutor (EAMA). */
@Data
public class InstrutorRequest {

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 200)
    private String nome;

    @Size(max = 30)
    private String rg;

    @Size(max = 30)
    private String orgaoEmissor;

    @Size(max = 20)
    private String cpf;

    @Size(max = 60)
    private String cha;
}
