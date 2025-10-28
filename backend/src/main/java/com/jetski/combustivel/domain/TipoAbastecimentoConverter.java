package com.jetski.combustivel.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA Converter para TipoAbastecimento.
 *
 * Converte entre o enum TipoAbastecimento e String no banco de dados.
 * Formato no banco: UPPER_CASE (ex: "PRE_LOCACAO", "POS_LOCACAO", "FROTA")
 */
@Converter(autoApply = true)
public class TipoAbastecimentoConverter implements AttributeConverter<TipoAbastecimento, String> {

    @Override
    public String convertToDatabaseColumn(TipoAbastecimento attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public TipoAbastecimento convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }

        try {
            return TipoAbastecimento.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unknown TipoAbastecimento: " + dbData + ". Valid values: PRE_LOCACAO, POS_LOCACAO, FROTA",
                e
            );
        }
    }
}
