package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.TipoPresenca;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO para presença de vendedor.
 *
 * @author Jetski Team
 * @since 0.10.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresencaVendedorResponse {

    private UUID id;
    private UUID vendedorId;
    private String vendedorNome;
    private LocalDate dtReferencia;
    private TipoPresenca tipo;

    /**
     * Valor base da diária do vendedor (configurado no cadastro)
     */
    private BigDecimal valorDiariaBase;

    /**
     * Valor calculado: valorDiariaBase * tipo.fator
     */
    private BigDecimal valorDiariaCalculado;

    /**
     * Valor ajustado manualmente (se houver)
     */
    private BigDecimal valorAjustado;

    /**
     * Valor efetivo a pagar: valorAjustado ?? valorDiariaCalculado
     */
    private BigDecimal valorEfetivo;

    /**
     * Motivo do ajuste (se houver)
     */
    private String motivoAjuste;

    private Instant createdAt;
}
