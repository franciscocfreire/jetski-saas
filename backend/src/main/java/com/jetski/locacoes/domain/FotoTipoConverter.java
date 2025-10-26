package com.jetski.locacoes.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA Converter for FotoTipo enum
 *
 * Converts between FotoTipo enum and database VARCHAR representation.
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Converter(autoApply = true)
public class FotoTipoConverter implements AttributeConverter<FotoTipo, String> {

    @Override
    public String convertToDatabaseColumn(FotoTipo attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public FotoTipo convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        return FotoTipo.valueOf(dbData.toUpperCase());
    }
}
