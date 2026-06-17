package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO: registrar/atualizar a habilitação do condutor de uma reserva.
 * via = "CHA" (já habilitado) ou "EMA" (emissão CHA-MTA-E + GRU).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HabilitacaoRequest {

    @NotBlank(message = "via é obrigatória (CHA ou EMA)")
    private String via;

    // Via CHA
    private String chaCategoria;
    private String chaNumero;
    private LocalDate chaValidade;

    // Via EMA
    private Boolean videoaulaAssistida;
    private Boolean anexoSaude;
    private Boolean anexoRegras;
    private Boolean anexoResidencia;

    // GRU (manual)
    private String gruNumero;
    private BigDecimal gruValor;
    private Boolean gruPago;
}
