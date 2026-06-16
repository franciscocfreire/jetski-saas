package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO: Recusar Pagamento Request
 *
 * <p>Recusa o pagamento (comprovante inválido) de uma reserva. A reserva
 * permanece não garantida (PENDENTE/BAIXA) e o cliente é notificado para
 * reenviar.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecusarPagamentoRequest {

    @NotBlank(message = "Motivo da recusa é obrigatório")
    private String motivo;
}
