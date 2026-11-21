package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.ids.FindingId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** A damage report number, stored the way it is printed so it can be read in a log. */
@Converter(autoApply = true)
public final class FindingIdColumn implements AttributeConverter<FindingId, String> {

    @Override
    public String convertToDatabaseColumn(FindingId value) {
        return value == null ? null : value.toString();
    }

    @Override
    public FindingId convertToEntityAttribute(String value) {
        return value == null ? null : FindingId.parse(value);
    }
}
