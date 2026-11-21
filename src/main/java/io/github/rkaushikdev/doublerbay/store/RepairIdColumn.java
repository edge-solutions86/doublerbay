package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.ids.RepairId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** A repair number, stored the way it is printed so it can be read in a log. */
@Converter(autoApply = true)
public final class RepairIdColumn implements AttributeConverter<RepairId, String> {

    @Override
    public String convertToDatabaseColumn(RepairId value) {
        return value == null ? null : value.toString();
    }

    @Override
    public RepairId convertToEntityAttribute(String value) {
        return value == null ? null : RepairId.parse(value);
    }
}
