package io.github.rkaushikdev.doublerbay.service;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One tail of the demonstration fleet, as the seed enrols it.
 *
 * @param serial the manufacturer's serial
 * @param marks the registration it wears
 * @param line its line number
 * @param delivered the day it was handed over
 */
public record SeedTail(String serial, String marks, int line, LocalDate delivered) {

    public SeedTail {
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(marks, "marks");
        Objects.requireNonNull(delivered, "delivered");
    }
}
