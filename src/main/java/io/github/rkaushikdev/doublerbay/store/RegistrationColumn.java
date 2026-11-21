package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.ids.Registration;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** A registration, stored as the marks. */
@Converter(autoApply = true)
public final class RegistrationColumn implements AttributeConverter<Registration, String> {

    @Override
    public String convertToDatabaseColumn(Registration value) {
        return value == null ? null : value.marks();
    }

    @Override
    public Registration convertToEntityAttribute(String value) {
        return value == null ? null : new Registration(value);
    }
}
