package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.DoublerbayError;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.fleet.Forecast;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.store.StoreContext;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Assembling the reports out of the repositories, in one place.
 *
 * <p>The counting lives in the other report types and takes plain lists, which is what keeps it
 * testable without a database. This is the layer that goes and gets those lists. Both the http
 * routes and the command line come through here, so the two cannot drift into answering the same
 * question differently, which is the failure that makes a desk stop believing either of them.
 *
 * <p>Every report here takes the day as an argument. None of them reach for today. A report run on
 * Monday for Friday's meeting and the same report run on Friday have to agree, and they only agree if
 * the day is passed in.
 *
 * <p>A tail with one counter reading has no rate, and a rate is what turns cycles into days. Rather
 * than refuse the whole report, that tail gets the flat rate the type is planned at. The report says
 * which tails that happened to.
 */
public final class DeskReports {

    /** The cycles a day the type is planned at, used only when a tail has no rate of its own. */
    public static final BigDecimal PLANNED_CYCLES_A_DAY = new BigDecimal("4.2");

    /** The flight hours a day the type is planned at, used only when a tail has no rate of its own. */
    public static final BigDecimal PLANNED_HOURS_A_DAY = new BigDecimal("8.1");

    /** How many days back a card counts as raised recently, unless a report is asked otherwise. */
    public static final int RECENT_DAYS = 30;

    private DeskReports() {}

    /** The tail's own rate, or the planning rate with a flag saying so. */
    public static TailRate rateFor(UnitOfWork unit, Serial serial) {
        UtilisationLog log = unit.aircraft().logFor(serial);
        try {
            return new TailRate(Forecast.fromLog(log), true);
        } catch (DoublerbayError noRate) {
            return new TailRate(new Forecast(log, PLANNED_CYCLES_A_DAY, PLANNED_HOURS_A_DAY), false);
        }
    }

    /** Everything with a limit on it against one tail, worst first. */
    public static DueReport dueOnTail(UnitOfWork unit, Serial serial, LocalDate on) {
        return dueOnTail(unit, serial, on, OptionalInt.empty(), new TypeCatalogue());
    }

    /** The same, cut down to what falls due within a number of days, the ones already over included. */
    public static DueReport dueOnTail(UnitOfWork unit, Serial serial, LocalDate on, int withinDays) {
        return dueOnTail(unit, serial, on, OptionalInt.of(withinDays), new TypeCatalogue());
    }

    /** Everything with a limit on it against one tail, against the structure a catalogue holds. */
    public static DueReport dueOnTail(UnitOfWork unit, Serial serial, LocalDate on, TypeCatalogue catalogue) {
        return dueOnTail(unit, serial, on, OptionalInt.empty(), catalogue);
    }

    /**
     * Everything with a limit on it against one tail, worst first, within a horizon when one is given.
     *
     * <p>Like the open count, this refuses a serial that is not on the register, and a tail that was
     * not in service on the day, rather than reporting an aeroplane nobody owns as having nothing due.
     */
    public static DueReport dueOnTail(
            UnitOfWork unit, Serial serial, LocalDate on, OptionalInt withinDays, TypeCatalogue catalogue) {
        StoreContext.contextFor(unit, catalogue, serial, on);
        TailRate rate = rateFor(unit, serial);
        Usage flown = unit.aircraft().logFor(serial).at(on);
        List<Deferral> live = unit.deferrals().live();
        Map<FindingId, Serial> onTail =
                unit.findings().serialsFor(live.stream().map(Deferral::against).toList());
        List<Deferral> deferrals = live.stream()
                .filter(one -> serial.equals(onTail.get(one.against())))
                .toList();
        List<DueItem> items = withinDays.isPresent()
                ? DueList.of(deferrals, serial, flown, rate.forecast(), on, withinDays.getAsInt())
                : DueList.of(deferrals, serial, flown, rate.forecast(), on);
        return new DueReport(serial, on, items, rate.measured());
    }

    /** The due list for every tail on the register, worst tail first. */
    public static List<DueReport> dueAcrossFleet(UnitOfWork unit, LocalDate on) {
        return dueAcrossFleet(unit, on, OptionalInt.empty());
    }

    /** The due list for every tail on the register within a number of days, worst tail first. */
    public static List<DueReport> dueAcrossFleet(UnitOfWork unit, LocalDate on, int withinDays) {
        return dueAcrossFleet(unit, on, OptionalInt.of(withinDays));
    }

    /**
     * The due list for every tail on the register, the most overrun first, then the most due, then in
     * serial order.
     */
    public static List<DueReport> dueAcrossFleet(UnitOfWork unit, LocalDate on, OptionalInt withinDays) {
        List<DueReport> reports = new ArrayList<>();
        TypeCatalogue catalogue = new TypeCatalogue();
        for (Aircraft one : unit.aircraft().all()) {
            reports.add(dueOnTail(unit, one.serial(), on, withinDays, catalogue));
        }
        reports.sort(Comparator.comparingInt((DueReport one) -> -one.overrun().size())
                .thenComparingInt(one -> -one.items().size())
                .thenComparing(one -> one.serial().number()));
        return List.copyOf(reports);
    }

    /** Only the tails with something already past its limit. */
    public static List<DueReport> grounded(UnitOfWork unit, LocalDate on) {
        return dueAcrossFleet(unit, on).stream().filter(DueReport::isGrounded).toList();
    }

    /** What is open on one tail as of a day, counting the last thirty days as recent. */
    public static DamageCount openOnTail(UnitOfWork unit, Serial serial, LocalDate on) {
        return openOnTail(unit, serial, on, RECENT_DAYS);
    }

    /**
     * What is open on one tail as of a day, refusing a tail that is not there.
     *
     * <p>The register is asked first even though the count does not need it. A mistyped serial
     * otherwise comes back as a clean aeroplane, which is the one wrong answer this report must never
     * give.
     */
    public static DamageCount openOnTail(UnitOfWork unit, Serial serial, LocalDate on, int recentDays) {
        unit.aircraft().get(serial);
        return DamageCount.countOpen(serial, unit.findings().onTail(serial), on, recentDays);
    }

    /** What is open across the whole register as of a day, counting the last thirty days as recent. */
    public static FleetDamage openAcrossFleet(UnitOfWork unit, LocalDate on) {
        return openAcrossFleet(unit, on, RECENT_DAYS);
    }

    /** What is open across every tail in service on a day. */
    public static FleetDamage openAcrossFleet(UnitOfWork unit, LocalDate on, int recentDays) {
        Map<Serial, List<Finding>> holdings = new LinkedHashMap<>();
        for (Aircraft one : unit.aircraft().inServiceOn(on)) {
            holdings.put(one.serial(), unit.findings().onTail(one.serial()));
        }
        return FleetDamage.countFleet(holdings, on, recentDays);
    }
}
