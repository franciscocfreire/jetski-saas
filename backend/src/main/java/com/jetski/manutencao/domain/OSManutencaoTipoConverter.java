package com.jetski.manutencao.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA Converter: OSManutencaoTipo ↔ String
 *
 * <p>Converts between enum and database column value.
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@Converter(autoApply = true)
public class OSManutencaoTipoConverter implements AttributeConverter<OSManutencaoTipo, String> {

    @Override
    public String convertToDatabaseColumn(OSManutencaoTipo attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getValue();
    }

    @Override
    public OSManutencaoTipo convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return OSManutencaoTipo.fromValue(dbData);
    }
}
