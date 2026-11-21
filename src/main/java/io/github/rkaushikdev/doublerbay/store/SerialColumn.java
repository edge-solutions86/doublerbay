package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.ids.Serial;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** A manufacturer serial number, stored without the MSN prefix it prints with. */
@Converter(autoApply = true)
public final class SerialColumn implements AttributeConverter<Serial, String> {

    @Override
    public String convertToDatabaseColumn(Serial value) {
        return value == null ? null : value.number();
    }

    @Override
    public Serial convertToEntityAttribute(String value) {
        return value == null ? null : new Serial(value);
    }
}
