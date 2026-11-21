package io.github.rkaushikdev.doublerbay.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * An inspection signed off, with the counters it was done at.
 *
 * @param on the day it was done
 * @param flown what the tail had flown when it was done
 */
public record SignOffIn(@NotNull LocalDate on, @NotNull @Valid UsageIn flown) {}
