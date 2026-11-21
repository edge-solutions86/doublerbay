package io.github.rkaushikdev.doublerbay.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * A move the desk is asking to make on a card.
 *
 * @param state the state to move it to
 * @param on the day of the move
 * @param by who made it
 * @param note why, if anybody wrote it down
 */
public record StateIn(@NotNull String state, @NotNull LocalDate on, @NotNull String by, String note) {

    public StateIn {
        note = note == null ? "" : note;
    }
}
