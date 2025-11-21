package com.jetski.manutencao.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA Converter: OSManutencaoStatus ↔ String
 *
 * <p>Converts between enum and database column value.
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@Converter(autoApply = true)
public class OSManutencaoStatusConverter implements AttributeConverter<OSManutencaoStatus, String> {

    @Override
    public String convertToDatabaseColumn(OSManutencaoStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getValue();
    }

    @Override
    public OSManutencaoStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return OSManutencaoStatus.fromValue(dbData);
    }
}
