package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.measure.Length;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** A length, stored as a whole number of thousandths of an inch. */
@Converter(autoApply = true)
public final class MilsColumn implements AttributeConverter<Length, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Length value) {
        return value == null ? null : value.mils();
    }

    @Override
    public Length convertToEntityAttribute(Integer value) {
        return value == null ? null : new Length(value);
    }
}
