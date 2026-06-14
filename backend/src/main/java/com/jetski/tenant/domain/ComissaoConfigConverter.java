package com.jetski.tenant.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * JPA Converter for ComissaoConfig to JSONB
 */
@Converter(autoApply = false)
@Slf4j
public class ComissaoConfigConverter implements AttributeConverter<ComissaoConfig, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(ComissaoConfig config) {
        if (config == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            log.error("Error converting ComissaoConfig to JSON", e);
            throw new RuntimeException("Failed to convert ComissaoConfig to JSON", e);
        }
    }

    @Override
    public ComissaoConfig convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return ComissaoConfig.padrao();
        }
        try {
            return objectMapper.readValue(json, ComissaoConfig.class);
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to ComissaoConfig", e);
            return ComissaoConfig.padrao();
        }
    }
}
