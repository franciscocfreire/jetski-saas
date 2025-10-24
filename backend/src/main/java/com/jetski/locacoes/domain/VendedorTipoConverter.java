package com.jetski.locacoes.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter for VendedorTipo enum
 *
 * Converts between Java enum (INTERNO/PARCEIRO) and database values (interno/parceiro)
 * to match PostgreSQL CHECK constraint
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Converter(autoApply = false)
public class VendedorTipoConverter implements AttributeConverter<VendedorTipo, String> {

    @Override
    public String convertToDatabaseColumn(VendedorTipo attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name().toLowerCase();
    }

    @Override
    public VendedorTipo convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        return VendedorTipo.valueOf(dbData.toUpperCase());
    }
}
