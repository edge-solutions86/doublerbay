package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.fleet.Forecast;
import io.github.rkaushikdev.doublerbay.fleet.Snapshot;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The routes for the register: tails, their counters, and what they are flying at.
 *
 * <p>A counter reading is the most frequent write this service takes and the one most likely to be
 * wrong, because it arrives from a maintenance system that has its own idea of what a flight cycle
 * is. The refusal it needs is the one already in the domain: a reading that runs a counter backwards
 * is refused with the reason, rather than accepted and left for a repeat inspection to be computed
 * off years later.
 *
 * <p>The forecast is a read and it is deliberately not stored. It is a straight line off the recent
 * rate and it changes every time a reading is posted, so a stored one is a stale one within a week.
 */
@RestController
@Tag(name = "fleet")
final class FleetRoutes {

    private final Deps deps;

    FleetRoutes(Deps deps) {
        this.deps = deps;
    }

    @PostMapping("/aircraft")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Action.AMEND_FLEET)
    @Operation(summary = "Put a tail on the register and open its log at delivery.")
    AircraftOut enrol(@Valid @RequestBody AircraftIn body) {
        Aircraft aircraft = body.toDomain();
        deps.run(unit -> {
            unit.aircraft().add(aircraft);
            unit.aircraft().openLog(UtilisationLog.opened(aircraft.serial(), aircraft.delivered()));
        });
        return Schemas.aircraftOut(aircraft);
    }

    @GetMapping("/aircraft")
    @Operation(summary = "Every tail on the register, or only the ones in service on a day.")
    List<AircraftOut> register(
            @RequestParam(name = "in_service_on", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate inServiceOn) {
        return deps.apply(unit -> {
            List<Aircraft> held = inServiceOn != null
                    ? unit.aircraft().inServiceOn(inServiceOn)
                    : unit.aircraft().all();
            return held.stream().map(Schemas::aircraftOut).toList();
        });
    }

    @GetMapping("/aircraft/{serial}")
    @Operation(summary = "One tail, by the serial the airframe keeps for life.")
    AircraftOut readTail(@PathVariable("serial") String serial) {
        return deps.apply(unit -> Schemas.aircraftOut(unit.aircraft().get(new Serial(serial))));
    }

    @GetMapping("/aircraft-by-marks/{marks}")
    @Operation(summary = "Whichever aeroplane is wearing these marks now.")
    AircraftOut readByMarks(@PathVariable("marks") String marks) {
        return deps.apply(unit -> Schemas.aircraftOut(unit.aircraft().byRegistration(Registration.parse(marks))));
    }

    @PostMapping("/aircraft/{serial}/readings")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Action.POST_READING)
    @Operation(summary = "Post a counter reading, refusing one that runs a counter backwards.")
    UtilisationOut postReading(@PathVariable("serial") String serial, @Valid @RequestBody ReadingIn body) {
        Serial held = new Serial(serial);
        return deps.apply(unit -> {
            unit.aircraft()
                    .postReading(held, new Snapshot(body.taken(), body.usage().toDomain()));
            unit.flush();
            return utilisationOf(unit, held);
        });
    }

    @GetMapping("/aircraft/{serial}/utilisation")
    @Operation(summary = "Every reading posted against one tail.")
    UtilisationOut readUtilisation(@PathVariable("serial") String serial) {
        return deps.apply(unit -> utilisationOf(unit, new Serial(serial)));
    }

    @GetMapping("/aircraft/{serial}/rate")
    @Operation(summary = "What the tail is flying at, taken off the recent stretch of its log.")
    RateOut readRate(
            @PathVariable("serial") String serial, @RequestParam(name = "over", defaultValue = "365") int over) {
        UtilisationLog log = deps.apply(unit -> unit.aircraft().logFor(new Serial(serial)));
        Forecast forecast = Forecast.fromLog(log, over);
        return new RateOut(
                serial,
                forecast.asOf(),
                Decimals.text(forecast.cyclesADay()),
                Decimals.text(forecast.hoursADay()),
                Decimals.text(forecast.flown().averageLeg()));
    }

    static ReadingOut readingOut(Snapshot snapshot) {
        return new ReadingOut(
                snapshot.taken(),
                new UsageOut(
                        snapshot.usage().cycles().count(),
                        Decimals.text(snapshot.usage().hours().hours())));
    }

    static UtilisationOut utilisationOf(UnitOfWork unit, Serial serial) {
        UtilisationLog log = unit.aircraft().logFor(serial);
        return new UtilisationOut(
                serial.number(),
                log.openedOn(),
                readingOut(log.latest()),
                log.snapshots().stream().map(FleetRoutes::readingOut).toList());
    }
}
