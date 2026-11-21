package io.github.rkaushikdev.doublerbay.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * The day a deferral was closed.
 *
 * @param on the day the damage under it was dealt with
 */
public record LiftIn(@NotNull LocalDate on) {}
