package com.jetski.locacoes.api.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Linha do módulo Reservas (busca): o essencial para localizar a reserva e
 * abrir a página de detalhe — nomes já resolvidos (cliente/modelo).
 */
@Value
@Builder
public class ReservaBuscaResponse {
    UUID id;
    String clienteNome;
    UUID clienteId;
    String modeloNome;
    LocalDateTime dataInicio;
    LocalDateTime dataFimPrevista;
    String status;
    String canal;
    String pagamentoStatus;
    BigDecimal valorTotal;
    boolean documentoEmitido;
}
