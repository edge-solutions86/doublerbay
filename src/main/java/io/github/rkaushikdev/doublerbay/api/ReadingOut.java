package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;

/**
 * One reading on a tail's log, as it goes back.
 *
 * @param taken the day the counters were read
 * @param usage what they read
 */
public record ReadingOut(LocalDate taken, UsageOut usage) {}
