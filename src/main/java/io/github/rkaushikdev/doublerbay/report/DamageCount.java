package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The open damage summary, which is the page the morning meeting looks at: open cards on one tail,
 * counted the three ways the meeting asks for.
 *
 * <p>Counting damage is less obvious than it sounds. A card can be re measured four times and it is
 * still one damage. Two cards raised by two shifts on the same dent are two cards and one damage, and
 * the desk only finds that out when somebody supersedes one of them. So the counts here are over cards
 * the desk still owns, and a superseded card is not one of them.
 *
 * <p>Grouping is by kind and by chapter rather than by panel. A panel level count runs to four hundred
 * rows on a narrowbody and nobody reads it. Chapter and kind fit on a page and answer the question the
 * meeting is actually asking, which is whether anything has moved since last week.
 *
 * <p>Nothing here reaches for the manual. This is a count of what was found, not of what it was
 * assessed as. Mixing the two produces a report that changes when a revision lands without a single
 * new damage having been found, and then nobody trusts any of it.
 *
 * <p>Each of the three counts is ranked most first, ties broken by name so two runs agree.
 *
 * @see FleetDamage
 */
public record DamageCount(
        Serial serial,
        LocalDate on,
        int total,
        List<Counted<DamageKind>> byKind,
        List<Counted<Integer>> byChapter,
        List<Counted<DamageOrigin>> byOrigin,
        int raisedRecently) {

    /** The chapter a card on the barrel files under. */
    public static final int FUSELAGE_CHAPTER = 53;

    /** The chapter a card on a wing files under. */
    public static final int WING_CHAPTER = 57;

    /** How many days back a card still counts as raised recently, unless the caller says otherwise. */
    static final int RECENT_DAYS = 30;

    /** A tail's open cards as of a day, counted three ways. */
    public DamageCount {
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(on, "on");
        byKind = List.copyOf(byKind);
        byChapter = List.copyOf(byChapter);
        byOrigin = List.copyOf(byOrigin);
    }

    /** Which chapter a card files under, taken off where it is. */
    public static int chapterOf(Finding finding) {
        return finding.point() instanceof FuselagePoint ? FUSELAGE_CHAPTER : WING_CHAPTER;
    }

    /** Count what is open on one tail as of a day, counting the last thirty days as recent. */
    public static DamageCount countOpen(Serial serial, List<Finding> findings, LocalDate on) {
        return countOpen(serial, findings, on, RECENT_DAYS);
    }

    /**
     * Count what is open on one tail as of a day: the cards nothing has superseded, found on or before
     * it, with the ones found inside the recent window counted again on their own.
     *
     * @throws BadReading when the recent window is less than a day
     */
    public static DamageCount countOpen(Serial serial, List<Finding> findings, LocalDate on, int recentDays) {
        if (recentDays < 1) {
            throw new BadReading("a recent window is at least a day");
        }
        LocalDate since = on.minusDays(recentDays);
        List<Finding> live = findings.stream()
                .filter(one -> one.supersededBy().isEmpty() && !one.foundOn().isAfter(on))
                .toList();
        int recently = (int)
                live.stream().filter(one -> !one.foundOn().isBefore(since)).count();
        return new DamageCount(
                serial,
                on,
                live.size(),
                ranked(live, Finding::kind, DamageKind::value),
                ranked(live, DamageCount::chapterOf, String::valueOf),
                ranked(live, Finding::origin, DamageOrigin::value),
                recently);
    }

    /** Most first, and ties broken by name so two runs agree. */
    private static <T> List<Counted<T>> ranked(List<Finding> live, Function<Finding, T> by, Function<T, String> name) {
        Map<T, Integer> counted = new LinkedHashMap<>();
        for (Finding one : live) {
            counted.merge(by.apply(one), 1, Integer::sum);
        }
        return counted.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<T, Integer> pair) -> -pair.getValue())
                        .thenComparing(pair -> name.apply(pair.getKey())))
                .map(pair -> new Counted<>(pair.getKey(), pair.getValue()))
                .toList();
    }

    /** Whether nothing is open. */
    public boolean isClean() {
        return total == 0;
    }

    /** The kind there is most of, or nothing if the tail is clean. */
    public Optional<DamageKind> worstKind() {
        return byKind.isEmpty() ? Optional.empty() : Optional.of(byKind.get(0).what());
    }

    /** How many open cards are of one kind. */
    public int ofKind(DamageKind kind) {
        return Counted.countOf(byKind, kind);
    }

    /** How many open cards file under one chapter. */
    public int inChapter(int chapter) {
        return Counted.countOf(byChapter, chapter);
    }

    /** {@code MSN 5127: nothing open} or {@code MSN 5127: 2 open, worst dent}. */
    @Override
    public String toString() {
        if (isClean()) {
            return serial + ": nothing open";
        }
        return serial + ": " + total + " open, worst " + byKind.get(0).what();
    }
}
