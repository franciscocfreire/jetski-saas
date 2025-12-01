package com.jetski.locacoes.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter for JetskiStatus enum
 *
 * Converts between Java enum and database string values.
 * Uses UPPERCASE to match seed data and enum conventions.
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
        // Store as UPPERCASE to match enum conventions and seed data
        return attribute.name();
    }

    @Override
    public JetskiStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        // Handle both upper and lowercase from database
        return JetskiStatus.valueOf(dbData.toUpperCase());
    }
}
