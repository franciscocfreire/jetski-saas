package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO: alteração da duração prevista de uma locação EM_CURSO (PRORROGAR).
 *
 * <p>O mínimo (5 minutos) é validado no service como regra de negócio
 * (BusinessException → 400), junto com o guard de status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDuracaoRequest {

    /** Nova duração prevista em minutos (substitui a atual; não soma). */
    @NotNull(message = "Duração prevista é obrigatória")
    private Integer duracaoPrevista;
}
