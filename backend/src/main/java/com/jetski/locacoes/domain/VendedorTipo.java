package com.jetski.locacoes.domain;

/**
 * Enum: VendedorTipo
 *
 * Represents the type of seller/partner in the rental system.
 *
 * Types:
 * - INTERNO: Internal employee (receives salary + commission)
 * - PARCEIRO: External partner (receives commission only)
 *
 * Business Rules:
 * - Commission rules may differ between types
 * - Partners typically have higher commission rates
 * - Internal sellers may have access to internal systems
 *
 * @author Jetski Team
 * @since 0.2.0
 */
public enum VendedorTipo {
    /**
     * Internal employee
     * Typically pier operators, managers who also sell
     */
    INTERNO,

    /**
     * External partner
     * Sales representatives, agencies, affiliates
     */
    PARCEIRO
}
