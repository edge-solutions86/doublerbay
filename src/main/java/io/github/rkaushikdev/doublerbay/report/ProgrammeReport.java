package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionEntry;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionLevel;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The corrosion programme report, which is the one that goes outside: one tail's corrosion programme
 * picture over a window.
 *
 * <p>This is not a count of corrosion. It is an argument that the programme is working, and the
 * number that carries the argument is the proportion of findings above level one. A fleet with two
 * hundred level one findings and nothing above is a programme doing its job. A fleet with four
 * findings, three of them level two, is a programme that has stopped.
 *
 * <p>The awkward part is the denominator. Counting level twos alone makes a small fleet look bad and a
 * large one look fine, so the rate has to be per panel inspected rather than per finding, and this
 * desk does not hold the inspection count. So the report carries the raw counts and the ratio it can
 * honestly compute, and it says which denominator it used rather than picking the flattering one.
 *
 * <p>Repeat locations are the other half. A panel that has been graded three times in five years is
 * telling the desk something no single grading does, and it is the input the programme review
 * actually acts on.
 */
public record ProgrammeReport(
        Serial serial,
        LocalDate since,
        LocalDate until,
        List<Counted<CorrosionLevel>> byLevel,
        List<RepeatLocation> repeats) {

    /** A panel graded this many times is worth a look at the programme, not the panel. */
    public static final int REPEAT_THRESHOLD = 3;

    /** The places a rate or a span in years is carried to. */
    static final int PLACES = 3;

    /** A tail, a window, its counts in level order, and its repeat locations most often first. */
    public ProgrammeReport {
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(since, "since");
        Objects.requireNonNull(until, "until");
        byLevel = List.copyOf(byLevel);
        repeats = List.copyOf(repeats);
    }

    /** Panels graded more than once, most often first and in part number order among equals. */
    public static List<RepeatLocation> repeatsIn(List<CorrosionEntry> entries) {
        Map<PanelId, List<LocalDate>> seen = new LinkedHashMap<>();
        for (CorrosionEntry one : entries) {
            seen.computeIfAbsent(one.part(), ignored -> new ArrayList<>()).add(one.foundOn());
        }
        List<RepeatLocation> found = new ArrayList<>();
        seen.forEach((part, days) -> {
            if (days.size() > 1) {
                found.add(new RepeatLocation(part, days.size(), Collections.min(days), Collections.max(days)));
            }
        });
        found.sort(Comparator.comparingInt((RepeatLocation one) -> -one.times())
                .thenComparing(one -> one.part().toString()));
        return List.copyOf(found);
    }

    /**
     * Build one tail's programme picture over a window, both ends included.
     *
     * @throws BadReading when the window ends before it starts
     */
    public static ProgrammeReport of(Serial serial, List<CorrosionEntry> entries, LocalDate since, LocalDate until) {
        if (until.isBefore(since)) {
            throw new BadReading("a reporting window ends after it starts");
        }
        List<CorrosionEntry> inside = entries.stream()
                .filter(one -> !one.foundOn().isBefore(since) && !one.foundOn().isAfter(until))
                .toList();
        Map<CorrosionLevel, Integer> counted = new EnumMap<>(CorrosionLevel.class);
        for (CorrosionEntry one : inside) {
            counted.merge(one.level(), 1, Integer::sum);
        }
        List<Counted<CorrosionLevel>> byLevel = counted.entrySet().stream()
                .sorted(Comparator.comparingInt(pair -> pair.getKey().value()))
                .map(pair -> new Counted<>(pair.getKey(), pair.getValue()))
                .toList();
        return new ProgrammeReport(serial, since, until, byLevel, repeatsIn(inside));
    }

    /** How many findings were graded in the window. */
    public int total() {
        return byLevel.stream().mapToInt(Counted::count).sum();
    }

    /** How many of them were above level one. */
    public int aboveLevelOne() {
        return byLevel.stream()
                .filter(row -> row.what() != CorrosionLevel.ONE)
                .mapToInt(Counted::count)
                .sum();
    }

    /** Whether the programme is holding, which is nothing above level one. */
    public boolean isCoping() {
        return aboveLevelOne() == 0;
    }

    /**
     * The share of findings above level one, over findings as the denominator, to three places with a
     * half rounding up; nought when nothing was graded.
     */
    public BigDecimal escalationRate() {
        if (total() == 0) {
            return BigDecimal.ZERO.setScale(PLACES);
        }
        return Decimals.over(BigDecimal.valueOf(aboveLevelOne()), total()).setScale(PLACES, RoundingMode.HALF_UP);
    }

    /** What the rate is over, said out loud rather than assumed. */
    public String denominator() {
        return "findings graded, not panels inspected";
    }

    /** The repeat locations graded often enough to be persistent. */
    public List<RepeatLocation> persistent() {
        return repeats.stream().filter(RepeatLocation::isPersistent).toList();
    }

    /** How many findings were graded at one level. */
    public int countAt(CorrosionLevel level) {
        return Counted.countOf(byLevel, level);
    }

    /** {@code MSN 5127: 1 graded, coping} or {@code MSN 5127: 4 graded, 1 above level one}. */
    @Override
    public String toString() {
        String state = isCoping() ? "coping" : aboveLevelOne() + " above level one";
        return serial + ": " + total() + " graded, " + state;
    }
}
