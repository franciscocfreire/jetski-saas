package com.jetski.fechamento.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO representando uma locação que foi alterada após a consolidação do fechamento.
 *
 * <p>Contém informações sobre o valor anterior (snapshot no momento da consolidação)
 * e o valor atual da locação.</p>
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocacaoAlterada {

    private UUID locacaoId;
    private String clienteNome;
    private String jetskiIdentificacao;
    private LocalDateTime dataCheckOut;

    // Valor no momento da consolidação (não temos snapshot, então comparamos com atual)
    private BigDecimal valorAnterior;

    // Valor atual da locação
    private BigDecimal valorAtual;

    // Diferença (valorAtual - valorAnterior)
    private BigDecimal diferenca;

    // Quando a locação foi alterada
    private Instant dataAlteracao;

    // Quem alterou (se disponível via auditoria)
    private String alteradoPor;
}
