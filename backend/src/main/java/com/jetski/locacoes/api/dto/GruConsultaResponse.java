package com.jetski.locacoes.api.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Linha do módulo GRUs: ciclo completo da GRU emitida via EMA — geração,
 * pagamento, emissão da documentação, envio à Marinha e confirmação
 * (devolutiva). {@code marinhaEnviadaEm} null = não enviado, falha OU
 * emissão anterior ao registro (V039).
 */
@Value
@Builder
public class GruConsultaResponse {
    UUID reservaId;
    UUID clienteId;
    String clienteNome;
    String gruNumero;
    BigDecimal gruValor;
    Boolean gruPago;
    Instant gruPagoEm;
    Instant gruGeradaEm;
    /** null enquanto a documentação não foi emitida (sem o que reenviar). */
    UUID documentoId;
    Instant documentoEmitidoEm;
    Instant marinhaEnviadaEm;
    Instant marinhaConfirmadaEm;
}
