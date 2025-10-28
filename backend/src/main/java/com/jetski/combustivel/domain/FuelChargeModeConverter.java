package com.jetski.combustivel.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA Converter para FuelChargeMode.
 *
 * Converte entre o enum FuelChargeMode e String no banco de dados.
 * Formato no banco: UPPER_CASE (ex: "INCLUSO", "MEDIDO", "TAXA_FIXA")
 */
@Converter(autoApply = true)
public class FuelChargeModeConverter implements AttributeConverter<FuelChargeMode, String> {

    @Override
    public String convertToDatabaseColumn(FuelChargeMode attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public FuelChargeMode convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }

        try {
            return FuelChargeMode.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unknown FuelChargeMode: " + dbData + ". Valid values: INCLUSO, MEDIDO, TAXA_FIXA",
                e
            );
        }
    }
}
