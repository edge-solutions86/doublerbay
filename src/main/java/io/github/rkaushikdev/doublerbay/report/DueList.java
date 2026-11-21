package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.fleet.Forecast;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.DueAt;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What falls due, and how close it is, ordered so the worst is at the top.
 *
 * <p>Every limit on this desk is against three clocks at once: flight cycles, flight hours and the
 * calendar. A deferral granted for five hundred cycles on an aeroplane doing four a day and one
 * granted for a hundred and eighty days on an aeroplane sitting in a hangar are the same limit
 * written two ways, and the list has to sort them against each other honestly.
 *
 * <p>So everything gets converted to days at the tail's own recent rate, and the smallest answer
 * wins. That number is a forecast and it is wrong the moment the aeroplane flies differently, which
 * is exactly why it is recomputed on every read and never stored.
 *
 * <p>A limit already passed sorts above everything and carries a negative margin, because the desk
 * needs the overrun in days rather than a flat "expired". An aeroplane three days past a deferral
 * and one eleven months past are different conversations with the authority.
 */
public final class DueList {

    /** How far ahead the desk plans, in days. Past this the rate is not worth trusting. */
    public static final int HORIZON_DAYS = 3650;

    private static final Comparator<DueItem> WORST_FIRST = Comparator.comparingInt(
                    (DueItem one) -> one.margin().days())
            .thenComparing(one -> one.against().toString());

    private DueList() {}

    /**
     * The tightest of the three clocks, expressed in days at this tail's rate.
     *
     * <p>A limit with no calendar term still gets a day count, because the rate turns cycles into
     * days. That is the whole point: it is the only way a cycles limit and a calendar limit can be
     * put in one sorted list. When two clocks give the same count, cycles beat hours and hours beat
     * the calendar.
     */
    public static Margin daysTo(DueAt due, Usage flown, Forecast rate, LocalDate today) {
        List<Margin> candidates = new ArrayList<>();
        due.cycles().ifPresent(limit -> {
            BigDecimal left = BigDecimal.valueOf(limit.count() - flown.cycles().count());
            candidates.add(new Margin(asDays(left, rate.cyclesADay()), Clock.CYCLES));
        });
        due.hours().ifPresent(limit -> {
            BigDecimal left = Decimals.over(
                    BigDecimal.valueOf(limit.minutes() - flown.hours().minutes()), Hours.MINUTES_PER_HOUR);
            candidates.add(new Margin(asDays(left, rate.hoursADay()), Clock.HOURS));
        });
        due.on()
                .ifPresent(on -> candidates.add(
                        new Margin(Math.toIntExact(ChronoUnit.DAYS.between(today, on)), Clock.CALENDAR)));
        if (candidates.isEmpty()) {
            throw new BadReading("a due point with no clocks on it cannot be counted down");
        }
        Margin tightest = candidates.get(0);
        for (Margin candidate : candidates) {
            if (candidate.days() < tightest.days()) {
                tightest = candidate;
            }
        }
        return tightest;
    }

    /**
     * Turn a margin in cycles or hours into days at a rate, rounding down.
     *
     * <p>Rounding down is deliberate on both sides of zero. Ten cycles left at three a day is three
     * days, not four, and ten cycles over at three a day is four days over, not three. The error
     * always lands against the aeroplane. A tail not flying at all never reaches a limit it has not
     * passed, and is due now on one it has.
     */
    private static int asDays(BigDecimal left, BigDecimal aDay) {
        if (aDay.signum() <= 0) {
            return left.signum() >= 0 ? HORIZON_DAYS : 0;
        }
        BigDecimal whole = Decimals.over(left, aDay).setScale(0, RoundingMode.FLOOR);
        return whole.min(BigDecimal.valueOf(HORIZON_DAYS)).intValueExact();
    }

    /** One deferral turned into a line on the due list. */
    public static DueItem deferralDue(Deferral deferral, Serial serial, Usage flown, Forecast rate, LocalDate today) {
        return new DueItem(
                serial,
                deferral.against(),
                "deferral, re inspect " + deferral.reInspectWith(),
                deferral.due(),
                daysTo(deferral.due(), flown, rate, today));
    }

    /** Everything due on one tail, worst first, two lines with the same margin in reference order. */
    public static List<DueItem> of(
            Iterable<Deferral> deferrals, Serial serial, Usage flown, Forecast rate, LocalDate today) {
        List<DueItem> items = lines(deferrals, serial, flown, rate, today);
        items.sort(WORST_FIRST);
        return List.copyOf(items);
    }

    /** Everything due on one tail within a number of days, the ones already over included, worst first. */
    public static List<DueItem> of(
            Iterable<Deferral> deferrals, Serial serial, Usage flown, Forecast rate, LocalDate today, int withinDays) {
        List<DueItem> items = lines(deferrals, serial, flown, rate, today);
        if (withinDays < 0) {
            throw new BadReading("a horizon is a count of days ahead");
        }
        items.removeIf(one -> one.margin().days() > withinDays);
        items.sort(WORST_FIRST);
        return List.copyOf(items);
    }

    private static List<DueItem> lines(
            Iterable<Deferral> deferrals, Serial serial, Usage flown, Forecast rate, LocalDate today) {
        List<DueItem> items = new ArrayList<>();
        for (Deferral one : deferrals) {
            items.add(deferralDue(one, serial, flown, rate, today));
        }
        return items;
    }

    /** The ones already past their limit, which is the grounding list. */
    public static List<DueItem> overrun(List<DueItem> items) {
        return items.stream().filter(DueItem::isOverrun).toList();
    }
}
