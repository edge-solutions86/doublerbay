package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionTask;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The routes for the repeat inspections the fitted repairs carry.
 *
 * <p>There is no route that creates one. A repeat inspection exists because a repair of a certain
 * category went on a certain panel, and letting a caller post one directly would let somebody put a
 * task on the fleet that no repair justifies, or worse, quietly not put one on a repair that needs
 * it.
 *
 * <p>Signing off is the write this actually needs, and it takes the counters as well as the date. A
 * sign off with a date and no reading resets the calendar clock and leaves the cycles clock where it
 * was, so the next one falls due at the wrong time and nobody notices for two years.
 */
@RestController
@Tag(name = "inspections")
final class InspectionRoutes {

    private final Deps deps;

    InspectionRoutes(Deps deps) {
        this.deps = deps;
    }

    @GetMapping("/inspections/aircraft/{serial}")
    @Operation(summary = "Every repeat inspection on one aeroplane, oldest repair first.")
    List<TaskOut> onTail(@PathVariable("serial") String serial) {
        return deps.apply(unit -> unit.inspections().onTail(Dependencies.tailOn(unit, serial)).stream()
                .map(InspectionRoutes::taskOut)
                .toList());
    }

    @GetMapping("/inspections/aircraft/{serial}/never-done")
    @Operation(summary = "The ones nobody has signed off yet, where the threshold still bites.")
    List<TaskOut> neverDone(@PathVariable("serial") String serial) {
        return deps.apply(unit -> unit.inspections().neverDone(Dependencies.tailOn(unit, serial)).stream()
                .map(InspectionRoutes::taskOut)
                .toList());
    }

    @GetMapping("/inspections/aircraft/{serial}/due")
    @Operation(summary = "Everything on one tail that has come due as of a day.")
    List<TaskOut> dueOn(
            @PathVariable("serial") String serial,
            @RequestParam(name = "on") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        return deps.apply(unit -> {
            Serial held = Dependencies.tailOn(unit, serial);
            Usage flown = unit.aircraft().logFor(held).at(on);
            return unit.inspections().dueOn(held, flown, on).stream()
                    .map(InspectionRoutes::taskOut)
                    .toList();
        });
    }

    @GetMapping("/inspections/{chapter}/{sequence}")
    @Operation(summary = "The repeat inspection one repair carries.")
    TaskOut readTask(@PathVariable("chapter") int chapter, @PathVariable("sequence") int sequence) {
        return deps.apply(unit -> taskOut(unit.inspections().get(new RepairId(chapter, sequence))));
    }

    @PostMapping("/inspections/{chapter}/{sequence}/done")
    @Requires(Action.DISPOSITION)
    @Operation(summary = "Record that the inspection was done, at the counters it was done at.")
    TaskOut signOff(
            @PathVariable("chapter") int chapter,
            @PathVariable("sequence") int sequence,
            @Valid @RequestBody SignOffIn body) {
        return deps.apply(unit -> {
            InspectionTask signed = unit.inspections()
                    .signOff(
                            new RepairId(chapter, sequence),
                            body.on(),
                            body.flown().toDomain());
            unit.flush();
            return taskOut(signed);
        });
    }

    /** A counter reading as it goes back. */
    static UsageOut usageOut(Usage flown) {
        return new UsageOut(flown.cycles().count(), Decimals.text(flown.hours().hours()));
    }

    /** One task as it goes back, with when it next falls due. */
    static TaskOut taskOut(InspectionTask task) {
        return new TaskOut(
                task.against().toString(),
                task.part().toString(),
                task.schedule().method().value(),
                task.schedule().threshold().toString(),
                task.schedule().repeat().map(Object::toString),
                task.embodied(),
                usageOut(task.atEmbodiment()),
                task.lastDone(),
                task.atLastDone().map(InspectionRoutes::usageOut),
                task.isAOneOff(),
                dueOut(task));
    }

    /** When it next falls due, or nothing for a one off already done. */
    private static Optional<DueOut> dueOut(InspectionTask task) {
        if (task.hasBeenDone() && task.isAOneOff()) {
            return Optional.empty();
        }
        return Optional.of(DueOut.of(task.due()));
    }
}
