package io.github.rkaushikdev.doublerbay.api;

/**
 * A counter reading as it goes back, with its hours as text.
 *
 * @param cycles the flight cycles
 * @param hours the flight hours to a tenth, as written
 */
public record UsageOut(int cycles, String hours) {}
