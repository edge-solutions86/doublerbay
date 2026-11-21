package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.List;

/**
 * How much damage is open on one tail, as it goes back.
 *
 * @param serial the tail
 * @param on the day it was asked for
 * @param total how many cards are open on it
 * @param raisedRecently how many of those were raised in the recent window
 * @param byKind the open cards by kind of damage, most first
 * @param byChapter the open cards by chapter, most first
 * @param byOrigin the open cards by cause, most first
 */
public record OpenTailOut(
        String serial,
        LocalDate on,
        int total,
        int raisedRecently,
        List<CountedOut> byKind,
        List<CountedOut> byChapter,
        List<CountedOut> byOrigin) {

    public OpenTailOut {
        byKind = List.copyOf(byKind);
        byChapter = List.copyOf(byChapter);
        byOrigin = List.copyOf(byOrigin);
    }
}
