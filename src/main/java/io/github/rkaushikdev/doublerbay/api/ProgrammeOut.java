package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.List;

/**
 * One tail's corrosion programme picture over a window, as it goes back.
 *
 * @param serial the tail
 * @param since the first day of the window
 * @param until the last day of the window
 * @param total how many findings were graded in it
 * @param aboveLevelOne how many of those were worse than level one
 * @param coping whether the programme is holding, which is nothing above level one
 * @param escalationRate the share above level one, as written
 * @param denominator what the share is taken over
 * @param byLevel the counts in level order
 * @param repeats the panels graded more than once, most often first
 */
public record ProgrammeOut(
        String serial,
        LocalDate since,
        LocalDate until,
        int total,
        int aboveLevelOne,
        boolean coping,
        String escalationRate,
        String denominator,
        List<LevelCountOut> byLevel,
        List<RepeatOut> repeats) {

    public ProgrammeOut {
        byLevel = List.copyOf(byLevel);
        repeats = List.copyOf(repeats);
    }
}
