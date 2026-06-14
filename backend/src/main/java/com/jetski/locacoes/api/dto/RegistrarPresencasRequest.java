package com.jetski.locacoes.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO para registro de presenças em lote (batch).
 * Permite registrar todas as presenças de um dia de uma vez.
 *
 * @author Jetski Team
 * @since 0.10.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrarPresencasRequest {

    @NotNull(message = "dtReferencia é obrigatória")
    private LocalDate dtReferencia;

    @NotNull(message = "presencas é obrigatório")
    @Size(min = 1, message = "Deve haver pelo menos uma presença")
    @Valid
    private List<PresencaVendedorRequest> presencas;
}
