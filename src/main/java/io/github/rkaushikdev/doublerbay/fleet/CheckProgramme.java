package io.github.rkaushikdev.doublerbay.fleet;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.DueAt;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * How often each kind of check comes round on this fleet.
 *
 * <p>A programme always sets the line, hangar and heavy checks, and may carry others. The kinds
 * keep the order they were handed in, which decides between two kinds that open the aeroplane up
 * equally far.
 */
public record CheckProgramme(Map<CheckKind, Interval> intervals) {

    /** A programme that sets an interval for each of the three scheduled checks. */
    public CheckProgramme {
        Objects.requireNonNull(intervals, "intervals");
        intervals = Collections.unmodifiableMap(new LinkedHashMap<>(intervals));
        for (CheckKind kind : List.of(CheckKind.LINE, CheckKind.HANGAR, CheckKind.HEAVY)) {
            if (!intervals.containsKey(kind)) {
                throw new BadReading("a programme sets an interval for the " + kind);
            }
        }
    }

    /** The interval a kind of check comes round on. */
    public Interval intervalFor(CheckKind kind) {
        Interval held = intervals.get(kind);
        if (held == null) {
            throw new BadReading("this programme has no " + kind + " on it");
        }
        return held;
    }

    /** When the same kind of check comes round again after this one, counted from its last day. */
    public DueAt nextAfter(CheckEvent event) {
        return intervalFor(event.kind()).dueFrom(event.atUsage(), event.finished());
    }

    /**
     * The most opened up check on the programme, once the due point has been reached, which is what
     * a finding rides on. Nothing while it has not.
     */
    public Optional<CheckKind> deepestBy(DueAt due, Usage flown, LocalDate on) {
        if (!due.isDue(flown, on)) {
            return Optional.empty();
        }
        CheckKind deepest = null;
        for (CheckKind kind : intervals.keySet()) {
            if (deepest == null || kind.access() > deepest.access()) {
                deepest = kind;
            }
        }
        return Optional.ofNullable(deepest);
    }

    /** {@code line check: 750.0 FH / 60 DY, hangar check: ...}, shallowest first. */
    @Override
    public String toString() {
        return intervals.entrySet().stream()
                .sorted(Comparator.comparingInt(pair -> pair.getKey().access()))
                .map(pair -> pair.getKey() + ": " + pair.getValue())
                .collect(Collectors.joining(", "));
    }
}
