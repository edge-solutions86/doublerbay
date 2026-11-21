package io.github.rkaushikdev.doublerbay.measure;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The readings a limit falls due on, any one of which is enough.
 *
 * <p>After an {@link Interval} has been landed here a due list only ever asks whether any of the
 * clocks has been reached, which keeps the whichever-comes-first rule in one place.
 */
public record DueAt(Optional<Cycles> cycles, Optional<Hours> hours, Optional<LocalDate> on) {

    /** A due point on whichever readings are given. */
    public DueAt {
        Objects.requireNonNull(cycles, "cycles");
        Objects.requireNonNull(hours, "hours");
        Objects.requireNonNull(on, "on");
    }

    /** A due point from whichever readings are given; a null reading is one it is silent on. */
    public static DueAt of(Cycles cycles, Hours hours, LocalDate on) {
        return new DueAt(Optional.ofNullable(cycles), Optional.ofNullable(hours), Optional.ofNullable(on));
    }

    /** A due point with nothing on it, which never falls due. */
    public static DueAt none() {
        return of(null, null, null);
    }

    /** Whether any one of the readings has been reached. */
    public boolean isDue(Usage flown, LocalDate today) {
        if (cycles.isPresent() && flown.cycles().isAtLeast(cycles.get())) {
            return true;
        }
        if (hours.isPresent() && flown.hours().isAtLeast(hours.get())) {
            return true;
        }
        return on.isPresent() && !today.isBefore(on.get());
    }

    /** The cycles left before the limit, none once it has been reached. */
    public Optional<Cycles> remainingCycles(Usage flown) {
        return cycles.map(limit -> flown.cycles().isAtLeast(limit) ? new Cycles(0) : limit.minus(flown.cycles()));
    }

    /** The days left before the limit, none once it has been reached. */
    public Optional<Integer> remainingDays(LocalDate today) {
        return on.map(day -> (int) Math.max(ChronoUnit.DAYS.between(today, day), 0));
    }

    /** {@code 15000 FC or 2027-03-15}, or {@code no limit}. */
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        cycles.ifPresent(count -> parts.add(count.count() + " FC"));
        hours.ifPresent(time -> parts.add(time.hours().toPlainString() + " FH"));
        on.ifPresent(day -> parts.add(day.toString()));
        return parts.isEmpty() ? "no limit" : String.join(" or ", parts);
    }
}
