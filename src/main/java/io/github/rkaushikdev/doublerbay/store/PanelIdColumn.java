package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.ids.PanelId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** A panel part number, stored the way it is printed so it can be read in a log. */
@Converter(autoApply = true)
public final class PanelIdColumn implements AttributeConverter<PanelId, String> {

    @Override
    public String convertToDatabaseColumn(PanelId value) {
        return value == null ? null : value.toString();
    }

    @Override
    public PanelId convertToEntityAttribute(String value) {
        return value == null ? null : PanelId.parse(value);
    }
}
