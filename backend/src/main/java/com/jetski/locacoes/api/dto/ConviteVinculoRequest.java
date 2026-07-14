package com.jetski.locacoes.api.dto;

/**
 * Convite de parceria de emissão delegada (V048).
 *
 * @param parceiroSlug slug da empresa convidada
 * @param papel        papel do CONVIDANTE: OPERADORA ou EMISSORA
 *
 * @author Jetski Team
 */
public record ConviteVinculoRequest(String parceiroSlug, String papel) {
}
