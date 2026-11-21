package io.github.rkaushikdev.doublerbay.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * A question going out to the manufacturer about one card.
 *
 * @param against the card it asks about
 * @param question what it asks
 * @param raised the day it was written
 * @param by the engineer writing it
 */
public record RaiseIn(
        @NotNull String against, @NotNull String question, @NotNull LocalDate raised, @NotNull String by) {}
