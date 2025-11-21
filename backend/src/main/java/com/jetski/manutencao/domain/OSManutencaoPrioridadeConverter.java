package com.jetski.manutencao.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA Converter: OSManutencaoPrioridade ↔ String
 *
 * <p>Converts between enum and database column value.
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@Converter(autoApply = true)
public class OSManutencaoPrioridadeConverter implements AttributeConverter<OSManutencaoPrioridade, String> {

    @Override
    public String convertToDatabaseColumn(OSManutencaoPrioridade attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getValue();
    }

    @Override
    public OSManutencaoPrioridade convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return OSManutencaoPrioridade.fromValue(dbData);
    }
}
