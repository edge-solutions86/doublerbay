package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.fleet.Series;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * A tail as it arrives to go on the register.
 *
 * @param serial the serial the airframe keeps for life
 * @param registration the marks it wears
 * @param series which series it is
 * @param lineNumber where it came off the line, one or later
 * @param delivered the day it was delivered
 */
public record AircraftIn(
        @NotNull String serial,
        @NotNull String registration,
        @NotNull String series,
        @NotNull @Min(1) Integer lineNumber,
        @NotNull LocalDate delivered) {

    /** The tail, refusing a serial, marks or series the register would not hold. */
    public Aircraft toDomain() {
        return new Aircraft(
                new Serial(serial), Registration.parse(registration), Series.fromValue(series), lineNumber, delivered);
    }
}
