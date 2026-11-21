package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.report.Counted;
import io.github.rkaushikdev.doublerbay.report.DamageCount;
import io.github.rkaushikdev.doublerbay.report.DeskReports;
import io.github.rkaushikdev.doublerbay.report.DueItem;
import io.github.rkaushikdev.doublerbay.report.DueReport;
import io.github.rkaushikdev.doublerbay.report.FleetDamage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The read only routes that answer what is due and what is open.
 *
 * <p>Nothing here writes and nothing here is guarded beyond the read role. A report that a mechanic
 * cannot pull up on a phone in a hangar is a report that gets replaced by a spreadsheet somebody
 * keeps on a laptop, and then the desk has two answers.
 *
 * <p>The day is a required parameter rather than defaulting to now. These numbers end up in a slide
 * for a meeting, and a slide that says what was due at the moment somebody clicked is not
 * reproducible. Making the day explicit means the same request run twice gives the same answer.
 */
@RestController
@Tag(name = "reports")
final class ReportRoutes {

    /** The furthest ahead, in days, this desk will plan against. */
    static final int MAX_HORIZON_DAYS = 3650;

    private final Deps deps;

    ReportRoutes(Deps deps) {
        this.deps = deps;
    }

    @GetMapping("/reports/due")
    @Operation(summary = "What falls due across the register, worst tail first.")
    FleetDueOut fleetDue(
            @RequestParam(name = "on") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on,
            @RequestParam(name = "within", required = false) Integer within) {
        OptionalInt horizon = checkedHorizon(within);
        List<DueReport> reports = deps.apply(unit -> DeskReports.dueAcrossFleet(unit, on, horizon));
        return fleetDueOut(on, reports);
    }

    @GetMapping("/reports/due/{serial}")
    @Operation(summary = "What falls due on one tail.")
    DueReportOut tailDue(
            @PathVariable("serial") String serial,
            @RequestParam(name = "on") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on,
            @RequestParam(name = "within", required = false) Integer within) {
        OptionalInt horizon = checkedHorizon(within);
        return deps.apply(
                unit -> reportOut(DeskReports.dueOnTail(unit, new Serial(serial), on, horizon, new TypeCatalogue())));
    }

    @GetMapping("/reports/grounded")
    @Operation(summary = "The tails with something already past its limit.")
    FleetDueOut fleetGrounded(@RequestParam(name = "on") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        List<DueReport> reports = deps.apply(unit -> DeskReports.grounded(unit, on));
        return fleetDueOut(on, reports);
    }

    @GetMapping("/reports/open")
    @Operation(summary = "How much damage is open across the register.")
    OpenFleetOut fleetOpen(
            @RequestParam(name = "on") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on,
            @RequestParam(name = "recent", defaultValue = "30") int recent) {
        FleetDamage counted = deps.apply(unit -> DeskReports.openAcrossFleet(unit, on, recent));
        return new OpenFleetOut(
                on,
                counted.total(),
                counted.cleanTails().stream().map(Serial::number).toList(),
                counted.tails().stream().map(ReportRoutes::openOut).toList());
    }

    @GetMapping("/reports/open/{serial}")
    @Operation(summary = "How much damage is open on one tail, by kind and by chapter.")
    OpenTailOut tailOpen(
            @PathVariable("serial") String serial,
            @RequestParam(name = "on") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on,
            @RequestParam(name = "recent", defaultValue = "30") int recent) {
        return deps.apply(unit -> openOut(DeskReports.openOnTail(unit, new Serial(serial), on, recent)));
    }

    /** Refuse a horizon this desk would not plan against; none asked for is none. */
    static OptionalInt checkedHorizon(Integer within) {
        if (within == null) {
            return OptionalInt.empty();
        }
        if (within < 0) {
            throw new BadReading("a horizon is a count of days ahead");
        }
        if (within > MAX_HORIZON_DAYS) {
            throw new BadReading("this desk does not plan past " + MAX_HORIZON_DAYS + " days");
        }
        return OptionalInt.of(within);
    }

    /** One limit on the due list as it goes back. */
    static DueItemOut itemOut(DueItem item) {
        return new DueItemOut(
                item.serial().number(),
                item.against().toString(),
                item.what(),
                item.due().toString(),
                item.margin().days(),
                item.margin().clock().value(),
                item.isOverrun());
    }

    /** One tail's due list as it goes back. */
    static DueReportOut reportOut(DueReport report) {
        return new DueReportOut(
                report.serial().number(),
                report.on(),
                report.measuredRate(),
                report.isGrounded(),
                report.items().stream().map(ReportRoutes::itemOut).toList());
    }

    /** One tail's open count as it goes back. */
    static OpenTailOut openOut(DamageCount counted) {
        return new OpenTailOut(
                counted.serial().number(),
                counted.on(),
                counted.total(),
                counted.raisedRecently(),
                countedOut(counted.byKind()),
                countedOut(counted.byChapter()),
                countedOut(counted.byOrigin()));
    }

    private static <T> List<CountedOut> countedOut(List<Counted<T>> rows) {
        return rows.stream()
                .map(one -> new CountedOut(String.valueOf(one.what()), one.count()))
                .toList();
    }

    private static FleetDueOut fleetDueOut(LocalDate on, List<DueReport> reports) {
        int overrun = reports.stream().mapToInt(one -> one.overrun().size()).sum();
        return new FleetDueOut(on, reports.stream().map(ReportRoutes::reportOut).toList(), overrun);
    }
}
