package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.measure.Hours;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Time under load, stored as whole minutes because the log book is. */
@Converter(autoApply = true)
public final class HoursColumn implements AttributeConverter<Hours, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Hours value) {
        return value == null ? null : value.minutes();
    }

    @Override
    public Hours convertToEntityAttribute(Integer value) {
        return value == null ? null : new Hours(value);
    }
}
