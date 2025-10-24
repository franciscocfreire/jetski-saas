package com.jetski.locacoes.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO: Disponibilidade Response
 *
 * Response containing modelo availability information for a given period.
 *
 * Provides capacity metrics:
 * - Total jetskis of this modelo
 * - Guaranteed reservations (ALTA priority with deposit)
 * - Regular reservations (BAIXA priority without deposit)
 * - Maximum allowed reservations (considering overbooking)
 * - Whether new reservation can be accepted
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisponibilidadeResponse {

    private UUID modeloId;
    private String modeloNome;
    private LocalDateTime dataInicio;
    private LocalDateTime dataFimPrevista;

    /**
     * Total number of available jetskis for this modelo
     */
    private long totalJetskis;

    /**
     * Number of guaranteed reservations (ALTA priority with deposit)
     * These block physical capacity
     */
    private long reservasGarantidas;

    /**
     * Total active reservations (both ALTA and BAIXA)
     */
    private long totalReservas;

    /**
     * Maximum reservations allowed based on overbooking configuration
     * Calculated as: ceil(totalJetskis * fatorOverbooking)
     */
    private long maximoReservas;

    /**
     * Whether a new reservation with deposit can be accepted
     */
    private boolean aceitaComSinal;

    /**
     * Whether a new reservation without deposit can be accepted
     */
    private boolean aceitaSemSinal;

    /**
     * Remaining guaranteed slots (for reservations with deposit)
     */
    private long vagasGarantidas;

    /**
     * Remaining regular slots (for reservations without deposit)
     */
    private long vagasRegulares;
}
