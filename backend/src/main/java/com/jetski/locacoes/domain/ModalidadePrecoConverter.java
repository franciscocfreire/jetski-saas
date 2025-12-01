package com.jetski.locacoes.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA Converter for ModalidadePreco enum
 *
 * Converts between ModalidadePreco enum and database VARCHAR representation.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Converter(autoApply = true)
public class ModalidadePrecoConverter implements AttributeConverter<ModalidadePreco, String> {

    @Override
    public String convertToDatabaseColumn(ModalidadePreco attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public ModalidadePreco convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        return ModalidadePreco.valueOf(dbData.toUpperCase());
    }
}
