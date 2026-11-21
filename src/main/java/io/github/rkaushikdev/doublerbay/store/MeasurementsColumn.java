package io.github.rkaushikdev.doublerbay.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.JsonText;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A measurement set, stored as one JSON document whose keys keep the order they were written in.
 *
 * <p>Eight kinds of damage carry eight different sets of numbers, and a table with forty nullable
 * columns in it is a table where nobody can tell which of them are supposed to be filled in. The
 * kind on the row says how to read the document, and the domain type validates it on the way back.
 */
@Converter
public final class MeasurementsColumn implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> DOCUMENT = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Object> value) {
        return value == null ? null : JsonText.object(value);
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Collections.unmodifiableMap(JSON.readValue(value, DOCUMENT));
        } catch (JsonProcessingException unreadable) {
            throw new BadReading("a stored measurement set is not a document: " + unreadable.getOriginalMessage());
        }
    }
}
