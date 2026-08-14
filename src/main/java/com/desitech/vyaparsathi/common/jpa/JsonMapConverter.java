package com.desitech.vyaparsathi.common.jpa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.Map;

/**
 * Bidirectional JSON &lt;=&gt; {@code Map<String, Object>} converter used
 * on entity fields backed by a JSON column (MySQL 5.7+ / MariaDB 10.2+).
 *
 * <p>The converter is applied per-field via
 * {@code @Convert(converter = JsonMapConverter.class)} rather than as
 * {@code autoApply=true} — several entities carry {@code Map} fields
 * that are handled by JPA's default element-collection mechanics, and
 * we do not want to hijack those.
 *
 * <p>Null / blank JSON round-trips to an empty map so callers can
 * safely {@code put}/{@code get} without a null-check.
 */
@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize custom attributes", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return Collections.emptyMap();
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            // Never blow up a read because of a bad JSON blob — surface as empty and let the app move on.
            return Collections.emptyMap();
        }
    }
}
