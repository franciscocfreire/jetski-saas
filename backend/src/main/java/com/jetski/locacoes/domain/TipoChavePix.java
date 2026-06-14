package com.jetski.locacoes.domain;

/**
 * Enum representing the types of PIX keys in Brazil's instant payment system.
 *
 * <p>PIX is the Brazilian instant payment system managed by the Central Bank.</p>
 *
 * <p>Valid key types:</p>
 * <ul>
 *   <li>CPF - Brazilian individual taxpayer ID (11 digits)</li>
 *   <li>CNPJ - Brazilian company taxpayer ID (14 digits)</li>
 *   <li>EMAIL - Valid email address</li>
 *   <li>TELEFONE - Phone number with country code (+55...)</li>
 *   <li>ALEATORIA - Random 32-character alphanumeric key</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.11.0
 */
public enum TipoChavePix {

    CPF("CPF"),
    CNPJ("CNPJ"),
    EMAIL("E-mail"),
    TELEFONE("Telefone"),
    ALEATORIA("Chave Aleatória");

    private final String descricao;

    TipoChavePix(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
