package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.report.ProgrammeReport;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionEntry;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionLevel;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The corrosion programme's own routes, kept apart from the damage routes.
 *
 * <p>A corrosion finding lives twice: as a damage card the desk closes when the metal is blended
 * out, and as a programme entry that never closes. These routes are the second one. Nothing here can
 * raise a finding, because a programme entry with no card behind it is a number somebody typed.
 *
 * <p>The reporting clock is the sharp end. A level three finding has three days to reach the
 * authority, and the only useful thing a service can do about that is make the list of ones that
 * have not gone out impossible to miss.
 */
@RestController
@Tag(name = "corrosion")
final class CorrosionRoutes {

    private final Deps deps;

    CorrosionRoutes(Deps deps) {
        this.deps = deps;
    }

    @GetMapping("/corrosion/unreported")
    @Operation(summary = "Level three findings the authority has not been told about yet.")
    List<EntryOut> unreported() {
        return deps.apply(CorrosionRoutes::unreportedIn);
    }

    @GetMapping("/corrosion/level/{level}")
    @Operation(summary = "Every finding across the fleet graded at one level.")
    List<EntryOut> atLevel(@PathVariable("level") int level) {
        return deps.apply(unit -> unit.corrosion().atLevel(levelFrom(level)).stream()
                .map(CorrosionRoutes::entryOut)
                .toList());
    }

    @GetMapping("/corrosion/aircraft/{serial}")
    @Operation(summary = "Every graded finding on one aeroplane, oldest first.")
    List<EntryOut> onTail(@PathVariable("serial") String serial) {
        return deps.apply(unit -> unit.corrosion().onTail(Dependencies.tailOn(unit, serial)).stream()
                .map(CorrosionRoutes::entryOut)
                .toList());
    }

    @GetMapping("/corrosion/aircraft/{serial}/programme")
    @Operation(summary = "One tail's programme picture over a window.")
    ProgrammeOut programme(
            @PathVariable("serial") String serial,
            @RequestParam(name = "since") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
            @RequestParam(name = "until") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        if (until.isBefore(since)) {
            throw new BadReading("a reporting window ends after it starts");
        }
        Held held = deps.apply(unit -> {
            Serial tail = Dependencies.tailOn(unit, serial);
            return new Held(tail, unit.corrosion().onTail(tail));
        });
        return programmeOut(ProgrammeReport.of(held.serial(), held.entries(), since, until));
    }

    @PostMapping("/corrosion/{reference}/reported")
    @Requires(Action.DISPOSITION)
    @Operation(summary = "Record that a level three finding went out to the authority.")
    List<EntryOut> markReported(@PathVariable("reference") String reference, @Valid @RequestBody ReportedIn body) {
        return deps.apply(unit -> {
            unit.corrosion().markReported(Dependencies.referenceFrom(reference), body.on());
            unit.flush();
            return unreportedIn(unit);
        });
    }

    /** One graded finding as it goes back. */
    static EntryOut entryOut(CorrosionEntry entry) {
        return new EntryOut(
                entry.part().toString(),
                entry.level().value(),
                entry.foundOn(),
                entry.mustBeReported(),
                entry.reportBy());
    }

    /** One tail's programme picture as it goes back. */
    static ProgrammeOut programmeOut(ProgrammeReport report) {
        return new ProgrammeOut(
                report.serial().number(),
                report.since(),
                report.until(),
                report.total(),
                report.aboveLevelOne(),
                report.isCoping(),
                Decimals.text(report.escalationRate()),
                report.denominator(),
                report.byLevel().stream()
                        .map(one -> new LevelCountOut(one.what().value(), one.count()))
                        .toList(),
                report.repeats().stream()
                        .map(one -> new RepeatOut(
                                one.part().toString(),
                                one.times(),
                                one.first(),
                                one.latest(),
                                Decimals.text(one.yearsBetween()),
                                one.isPersistent()))
                        .toList());
    }

    /**
     * The level with this number, refused as a bad reading that lists the levels there are.
     *
     * <p>A caller asking for a level this programme does not grade has made a bad request, not broken
     * the server.
     */
    static CorrosionLevel levelFrom(int value) {
        List<String> every = new ArrayList<>();
        for (CorrosionLevel one : CorrosionLevel.values()) {
            if (one.value() == value) {
                return one;
            }
            every.add(String.valueOf(one.value()));
        }
        throw new BadReading(value + " is not a corrosion level, it is one of " + String.join(", ", every));
    }

    private static List<EntryOut> unreportedIn(UnitOfWork unit) {
        return unit.corrosion().unreported().stream()
                .map(CorrosionRoutes::entryOut)
                .toList();
    }

    /** A tail checked against the register, and its graded findings, read in one unit of work. */
    private record Held(Serial serial, List<CorrosionEntry> entries) {}
}
