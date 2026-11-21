package io.github.rkaushikdev.doublerbay.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * An answer that carries no sheet: a rejection, or a question taken back.
 *
 * @param on the day of the answer
 * @param by the engineer recording it
 */
public record AnsweredIn(@NotNull LocalDate on, @NotNull String by) {}
