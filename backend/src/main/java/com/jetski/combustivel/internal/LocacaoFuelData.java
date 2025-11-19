package com.jetski.combustivel.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO para quebrar dependência cíclica entre combustivel e locacoes.
 *
 * Contém apenas os dados necessários para cálculo de custo de combustível,
 * evitando que FuelPolicyService dependa diretamente de Locacao.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocacaoFuelData {

    /**
     * ID da locação.
     */
    private UUID id;

    /**
     * ID do tenant.
     */
    private UUID tenantId;

    /**
     * ID do jetski.
     */
    private UUID jetskiId;

    /**
     * Data/hora do check-out (necessário para preço do dia).
     */
    private Instant dataCheckOut;

    /**
     * Minutos faturáveis calculados (necessário para TAXA_FIXA).
     */
    private Integer minutosFaturaveis;
}
