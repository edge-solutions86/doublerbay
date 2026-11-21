package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.measure.Cycles;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** A count of pressurisations, stored as the count. */
@Converter(autoApply = true)
public final class CyclesColumn implements AttributeConverter<Cycles, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Cycles value) {
        return value == null ? null : value.count();
    }

    @Override
    public Cycles convertToEntityAttribute(Integer value) {
        return value == null ? null : new Cycles(value);
    }
}
