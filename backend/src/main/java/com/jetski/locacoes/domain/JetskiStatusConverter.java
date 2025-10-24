package com.jetski.locacoes.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter for JetskiStatus enum
 *
 * Converts between Java enum (DISPONIVEL/MANUTENCAO/etc) and database values (disponivel/manutencao/etc)
 * to match PostgreSQL CHECK constraint
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Converter(autoApply = false)
public class JetskiStatusConverter implements AttributeConverter<JetskiStatus, String> {

    @Override
    public String convertToDatabaseColumn(JetskiStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name().toLowerCase();
    }

    @Override
    public JetskiStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        return JetskiStatus.valueOf(dbData.toUpperCase());
    }
}
