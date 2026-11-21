package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;

/**
 * A panel graded more than once, as it goes back.
 *
 * @param part the panel
 * @param times how many times it was graded
 * @param first the first time
 * @param latest the latest time
 * @param yearsBetween the years from the first to the latest, as written
 * @param persistent whether it has come back often enough to question the programme
 */
public record RepeatOut(
        String part, int times, LocalDate first, LocalDate latest, String yearsBetween, boolean persistent) {}
