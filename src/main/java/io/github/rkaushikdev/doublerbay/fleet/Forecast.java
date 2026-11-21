package io.github.rkaushikdev.doublerbay.fleet;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.DueAt;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A tail's log plus the rate it is flying at, which says when a limit is going to fall due.
 *
 * <p>A repeat inspection at three thousand cycles is not a date until somebody knows what the
 * aeroplane does in a week. A short haul tail gets there in eighteen months and a long haul one on
 * the same limit takes four years, and the planning department needs the date rather than the
 * counter because that is what a slot in a hangar is booked against.
 *
 * <p>The projection is deliberately a straight line off the recent rate. Anything cleverer is
 * pretending to know about next summer's schedule, and the number is re forecast every time a
 * reading is posted anyway. What matters is that all three clocks are projected and the earliest
 * one wins, which is the same rule the limit itself is written under.
 */
public record Forecast(UtilisationLog log, BigDecimal cyclesADay, BigDecimal hoursADay, int horizonDays) {

    /**
     * Twenty years. Past this the answer is not a forecast, it is arithmetic on a rate that will have
     * changed many times over.
     */
    public static final int DEFAULT_HORIZON_DAYS = 7300;

    /** A forecast at a rate that does not run backwards, over a horizon of at least a day. */
    public Forecast {
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(cyclesADay, "cyclesADay");
        Objects.requireNonNull(hoursADay, "hoursADay");
        if (cyclesADay.signum() < 0 || hoursADay.signum() < 0) {
            throw new BadReading(log.serial() + ": a rate does not run backwards");
        }
        if (horizonDays < 1) {
            throw new BadReading("a forecast horizon is at least a day");
        }
    }

    /** A forecast at a rate, over the default horizon. */
    public Forecast(UtilisationLog log, BigDecimal cyclesADay, BigDecimal hoursADay) {
        this(log, cyclesADay, hoursADay, DEFAULT_HORIZON_DAYS);
    }

    /** A forecast at the rate the log shows over its last year. */
    public static Forecast fromLog(UtilisationLog log) {
        return fromLog(log, UtilisationLog.RATE_WINDOW_DAYS);
    }

    /** A forecast at the rate the log shows over its last stretch of this many days. */
    public static Forecast fromLog(UtilisationLog log, int over) {
        return fromLog(log, over, DEFAULT_HORIZON_DAYS);
    }

    /** A forecast at the rate the log shows over its last stretch, out to a horizon. */
    public static Forecast fromLog(UtilisationLog log, int over, int horizonDays) {
        DailyRate rate = log.dailyRate(over);
        return new Forecast(log, rate.cyclesADay(), rate.hoursADay(), horizonDays);
    }

    /** The day of the reading the forecast runs from. */
    public LocalDate asOf() {
        return log.latest().taken();
    }

    /** The counters at that reading. */
    public Usage flown() {
        return log.latest().usage();
    }

    /** The counters on a day: read off the log up to its latest reading, run forward at the rate after it. */
    public Usage projected(LocalDate day) {
        if (!day.isAfter(asOf())) {
            return log.at(day);
        }
        long ahead = ChronoUnit.DAYS.between(asOf(), day);
        Cycles cycles = flown().cycles().plus(new Cycles(truncated(Decimals.times(cyclesADay, ahead))));
        int minutes = Decimals.toInt(Decimals.times(Decimals.times(hoursADay, Hours.MINUTES_PER_HOUR), ahead));
        return new Usage(cycles, flown().hours().plus(new Hours(minutes)));
    }

    /** The earliest day any of the three clocks reaches the limit, forecasting from the latest reading. */
    public Optional<LocalDate> whenDue(DueAt due) {
        return whenDue(due, asOf());
    }

    /**
     * The earliest day any of the three clocks reaches the limit, and never before today.
     *
     * <p>Nothing means no clock on this limit gets there inside the horizon, which for a rarely
     * flown tail on a cycle limit is a real answer and not a failure to compute one.
     */
    public Optional<LocalDate> whenDue(DueAt due, LocalDate today) {
        if (today.isBefore(asOf())) {
            throw new BadReading(log.serial() + ": cannot forecast from before the last reading");
        }
        List<BigDecimal> offsets = new ArrayList<>();
        due.cycles().flatMap(this::daysToCycles).ifPresent(offsets::add);
        due.hours().flatMap(this::daysToHours).ifPresent(offsets::add);
        due.on().ifPresent(on -> offsets.add(BigDecimal.valueOf(Math.max(ChronoUnit.DAYS.between(asOf(), on), 0))));
        if (offsets.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal soonest = offsets.get(0);
        for (BigDecimal offset : offsets) {
            soonest = offset.min(soonest);
        }
        if (soonest.compareTo(BigDecimal.valueOf(horizonDays)) > 0) {
            return Optional.empty();
        }
        LocalDate landing = asOf().plusDays(soonest.longValueExact());
        return Optional.of(landing.isAfter(today) ? landing : today);
    }

    /** How many days from the latest reading until the limit lands, when it lands inside the horizon. */
    public Optional<Integer> daysUntil(DueAt due) {
        return daysUntil(due, asOf());
    }

    /** How many days from today until the limit lands, none once it has, when it lands inside the horizon. */
    public Optional<Integer> daysUntil(DueAt due, LocalDate today) {
        return whenDue(due, today).map(landing -> (int) Math.max(ChronoUnit.DAYS.between(today, landing), 0));
    }

    private Optional<BigDecimal> daysToCycles(Cycles target) {
        if (flown().cycles().isAtLeast(target)) {
            return Optional.of(BigDecimal.ZERO);
        }
        if (cyclesADay.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal shortBy = BigDecimal.valueOf(target.minus(flown().cycles()).count());
        return Optional.of(Decimals.over(shortBy, cyclesADay).setScale(0, RoundingMode.CEILING));
    }

    private Optional<BigDecimal> daysToHours(Hours target) {
        if (flown().hours().isAtLeast(target)) {
            return Optional.of(BigDecimal.ZERO);
        }
        if (hoursADay.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal shortBy =
                Decimals.over(BigDecimal.valueOf(target.minus(flown().hours()).minutes()), Hours.MINUTES_PER_HOUR);
        return Optional.of(Decimals.over(shortBy, hoursADay).setScale(0, RoundingMode.CEILING));
    }

    /** The whole part of a count that is never negative, the fraction dropped. */
    private static int truncated(BigDecimal value) {
        return value.setScale(0, RoundingMode.DOWN).intValueExact();
    }

    /** {@code MSN 5127: 5 FC and 9 FH a day}. */
    @Override
    public String toString() {
        return log.serial() + ": " + Decimals.text(cyclesADay) + " FC and " + Decimals.text(hoursADay) + " FH a day";
    }
}
