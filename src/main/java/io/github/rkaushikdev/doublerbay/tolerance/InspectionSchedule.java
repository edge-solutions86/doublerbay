package io.github.rkaushikdev.doublerbay.tolerance;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.DueAt;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * When a repair gets looked at, how, and how often after that.
 *
 * <p>The threshold is the flying before the first look. A schedule with no repeat is a temporary
 * repair's replacement limit, which is done once. {@link Schedules#scheduleFor} works one out.
 */
public record InspectionSchedule(InspectionMethod method, Interval threshold, Optional<Interval> repeat) {

    /** A method, a threshold, and the repeat if there is one. */
    public InspectionSchedule {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(threshold, "threshold");
        Objects.requireNonNull(repeat, "repeat");
    }

    /** A schedule from what was to hand; a null repeat is a replacement limit. */
    public static InspectionSchedule of(InspectionMethod method, Interval threshold, Interval repeat) {
        return new InspectionSchedule(method, threshold, Optional.ofNullable(repeat));
    }

    /** Whether the inspection comes round again after the first one. */
    public boolean isRepeating() {
        return repeat.isPresent();
    }

    /** The first look, the threshold from when the repair went on. */
    public DueAt firstDue(Usage flown, LocalDate embodied) {
        return threshold.dueFrom(flown, embodied);
    }

    /**
     * The next look, the repeat from the last one.
     *
     * @throws BadReading when the schedule has no repeat
     */
    public DueAt nextDue(Usage flown, LocalDate lastDone) {
        if (repeat.isEmpty()) {
            throw new BadReading("this schedule has no repeat, it is a replacement limit");
        }
        return repeat.get().dueFrom(flown, lastDone);
    }

    /**
     * {@code detailed visual at 55000 FC then every 27500 FC}, or {@code general visual once by 12 MO}
     * for a replacement limit.
     */
    @Override
    public String toString() {
        if (repeat.isEmpty()) {
            return method + " once by " + threshold;
        }
        return method + " at " + threshold + " then every " + repeat.get();
    }
}
