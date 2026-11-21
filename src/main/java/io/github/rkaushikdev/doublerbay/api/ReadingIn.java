package io.github.rkaushikdev.doublerbay.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * One posted counter reading.
 *
 * @param taken the day the counters were read
 * @param usage what they read
 */
public record ReadingIn(@NotNull LocalDate taken, @NotNull @Valid UsageIn usage) {}
