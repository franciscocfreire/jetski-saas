package com.jetski.combustivel.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA Converter para FuelPolicyType.
 *
 * Converte entre o enum FuelPolicyType e String no banco de dados.
 * Formato no banco: UPPER_CASE (ex: "GLOBAL", "MODELO", "JETSKI")
 */
@Converter(autoApply = true)
public class FuelPolicyTypeConverter implements AttributeConverter<FuelPolicyType, String> {

    @Override
    public String convertToDatabaseColumn(FuelPolicyType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public FuelPolicyType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }

        try {
            return FuelPolicyType.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unknown FuelPolicyType: " + dbData + ". Valid values: GLOBAL, MODELO, JETSKI",
                e
            );
        }
    }
}
