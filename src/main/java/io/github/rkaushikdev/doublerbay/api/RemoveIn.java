package io.github.rkaushikdev.doublerbay.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * The day a repair came off.
 *
 * @param on the day it was taken off
 */
public record RemoveIn(@NotNull LocalDate on) {}
