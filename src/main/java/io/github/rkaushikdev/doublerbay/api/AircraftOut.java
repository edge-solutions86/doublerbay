package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A tail on the register, as it goes back.
 *
 * @param serial the serial the airframe keeps for life
 * @param registration the marks it wears now
 * @param series which series it is
 * @param lineNumber where it came off the line
 * @param delivered the day it was delivered
 * @param withdrawn the day it left service, if it has
 */
public record AircraftOut(
        String serial,
        String registration,
        String series,
        int lineNumber,
        LocalDate delivered,
        Optional<LocalDate> withdrawn) {}
