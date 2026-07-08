package com.jetski.locacoes.api.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reserva na visão AGENDA (grade por jetski): dados de exibição + o trio de
 * PRONTIDÃO que decide o embarque — pagamento, habilitação e termo.
 * {@code jetskiId} null = reserva do portal ainda sem alocação (faixa
 * "A alocar" da grade).
 */
@Value
@Builder
public class AgendaReservaResponse {
    UUID id;
    UUID clienteId;
    String clienteNome;
    UUID modeloId;
    String modeloNome;
    UUID jetskiId;
    String jetskiSerie;
    LocalDateTime dataInicio;
    LocalDateTime dataFimPrevista;
    String status;
    String canal;
    BigDecimal valorTotal;
    boolean pagamentoOk;
    boolean habilitacaoOk;
    /** CHA | EMA | null — para o tooltip do indicador. */
    String habilitacaoVia;
    boolean termoOk;
    /** pagamento + habilitação + termo — pode embarcar. */
    boolean prontaParaCheckin;
}
