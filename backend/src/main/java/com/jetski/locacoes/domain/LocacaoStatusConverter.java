package com.jetski.locacoes.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA Converter for LocacaoStatus enum
 *
 * Converts between LocacaoStatus enum and database VARCHAR representation.
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Converter(autoApply = true)
public class LocacaoStatusConverter implements AttributeConverter<LocacaoStatus, String> {

    @Override
    public String convertToDatabaseColumn(LocacaoStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public LocacaoStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        return LocacaoStatus.valueOf(dbData.toUpperCase());
    }
}
