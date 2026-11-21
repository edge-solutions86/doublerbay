package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything that happened to one card, oldest first.
 *
 * @param reference the card
 * @param serial the tail it is on
 * @param opened the day of the first entry
 * @param latest the day of the last entry
 * @param daysOpen the days from the first entry to the last
 * @param entries every entry from every register, in the order they happened
 */
public record StoryOut(
        String reference,
        String serial,
        LocalDate opened,
        LocalDate latest,
        int daysOpen,
        List<StoryEntryOut> entries) {}
