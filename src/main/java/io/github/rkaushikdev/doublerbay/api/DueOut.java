package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.DueAt;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Where a limit lands, clock by clock, and as the desk writes it.
 *
 * @param cycles the flight cycles it lands at, when it has a cycles clock
 * @param hours the flight hours it lands at, as written, when it has an hours clock
 * @param on the day it lands, when it has a calendar clock
 * @param written the whole limit as it is printed
 */
public record DueOut(Optional<Integer> cycles, Optional<String> hours, Optional<LocalDate> on, String written) {

    public DueOut {
        Objects.requireNonNull(cycles, "cycles");
        Objects.requireNonNull(hours, "hours");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(written, "written");
    }

    /** One limit as it goes back. */
    static DueOut of(DueAt due) {
        return new DueOut(
                due.cycles().map(Cycles::count),
                due.hours().map(one -> Decimals.text(one.hours())),
                due.on(),
                due.toString());
    }
}
