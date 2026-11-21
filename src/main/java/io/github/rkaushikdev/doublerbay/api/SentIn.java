package io.github.rkaushikdev.doublerbay.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * The day an order actually went.
 *
 * @param on the day it was sent
 */
public record SentIn(@NotNull LocalDate on) {}
