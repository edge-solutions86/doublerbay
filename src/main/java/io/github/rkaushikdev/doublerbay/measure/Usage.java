package io.github.rkaushikdev.doublerbay.measure;

import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Both counters at one moment. Deliberately not orderable.
 *
 * <p>A tail can be ahead of another on cycles and behind it on hours, which is exactly what a
 * short haul aircraft looks like next to a long haul one of the same age. Anything that wants to
 * ask whether one usage has reached another has to say which counter it means, or ask whether both
 * have.
 */
public record Usage(Cycles cycles, Hours hours) {

    /** Both counters, neither missing. */
    public Usage {
        Objects.requireNonNull(cycles, "cycles");
        Objects.requireNonNull(hours, "hours");
    }

    /** Nothing flown on either counter. */
    public static Usage zero() {
        return new Usage(new Cycles(0), new Hours(0));
    }

    /** The two usages together, counter by counter. */
    public Usage plus(Usage other) {
        return new Usage(cycles.plus(other.cycles), hours.plus(other.hours));
    }

    /** This usage less another, counter by counter. */
    public Usage minus(Usage other) {
        return new Usage(cycles.minus(other.cycles), hours.minus(other.hours));
    }

    /** True once either counter has caught the target, which is how limits fall due. */
    public boolean reachesEither(Usage target) {
        return cycles.isAtLeast(target.cycles) || hours.isAtLeast(target.hours);
    }

    /** True once both counters have caught the target. */
    public boolean reachesBoth(Usage target) {
        return cycles.isAtLeast(target.cycles) && hours.isAtLeast(target.hours);
    }

    /**
     * Hours per cycle, to two places, which is how the fleet tells its short haul tails apart.
     *
     * @throws ArithmeticException when no cycles have been flown, so there is no average leg
     */
    public BigDecimal averageLeg() {
        if (cycles.count() == 0) {
            throw new ArithmeticException("no cycles flown, so there is no average leg");
        }
        BigDecimal perHour = Decimals.over(BigDecimal.valueOf(hours.minutes()), Hours.MINUTES_PER_HOUR);
        return Decimals.quantize(Decimals.over(perHour, cycles.count()), 2);
    }

    /** {@code 1200 FC / 2400.0 FH}. */
    @Override
    public String toString() {
        return cycles + " / " + hours;
    }
}
