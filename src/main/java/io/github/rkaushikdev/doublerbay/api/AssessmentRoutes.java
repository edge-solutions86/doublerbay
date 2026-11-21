package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.assess.Assessment;
import io.github.rkaushikdev.doublerbay.assess.Assessor;
import io.github.rkaushikdev.doublerbay.assess.PanelAssessment;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.store.StoreContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The routes that answer what happens to a damage, as of a date.
 *
 * <p>An assessment is a read and it is never stored. It is a function of the card, the aeroplane,
 * the structure and the revision of the book in force on the day, and every one of those moves.
 * Storing the answer means serving a stale one after the next revision lands, which is the exact
 * failure the whole revision model exists to prevent.
 *
 * <p>The date is a parameter for the same reason. Asking what this was assessed as in March and
 * asking what it would be assessed as today are different questions and the desk asks both, so the
 * route takes the day rather than assuming now.
 */
@RestController
@Tag(name = "assessment")
final class AssessmentRoutes {

    private final Deps deps;

    AssessmentRoutes(Deps deps) {
        this.deps = deps;
    }

    @GetMapping("/findings/{reference}/assessment")
    @Operation(summary = "Walk one card through the book as it stood on a day.")
    AssessmentOut assessOne(
            @PathVariable("reference") String reference,
            @RequestParam(name = "on", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        LocalDate day = Dependencies.dayOr(deps, Optional.ofNullable(on));
        return deps.apply(unit -> {
            Finding finding = unit.findings().latest(Dependencies.referenceFrom(reference));
            TailContext tail = StoreContext.contextFor(unit, deps.catalogue(), finding.serial(), day);
            return Schemas.assessmentOut(Assessor.assess(deps.book(), tail, finding));
        });
    }

    @GetMapping("/aircraft/{serial}/assessment")
    @Operation(summary = "Assess every open card on one tail, panel by panel.")
    TailAssessmentOut assessTail(
            @PathVariable("serial") String serial,
            @RequestParam(name = "on", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        LocalDate day = Dependencies.dayOr(deps, Optional.ofNullable(on));
        Serial held = new Serial(serial);
        Opened opened = deps.apply(unit -> new Opened(
                StoreContext.contextFor(unit, deps.catalogue(), held, day),
                unit.findings().openOn(held)));

        if (opened.cards().isEmpty()) {
            return new TailAssessmentOut(held.number(), day, "allowable as it stands", List.of());
        }

        List<PanelAssessment> panels = PanelAssessment.assessByPanel(deps.book(), opened.tail(), opened.cards());
        List<Assessment> every =
                panels.stream().flatMap(panel -> panel.assessments().stream()).toList();
        return new TailAssessmentOut(
                held.number(),
                day,
                Assessor.worstDisposition(every).value(),
                panels.stream()
                        .map(panel -> new PanelAssessmentOut(
                                panel.part().toString(),
                                panel.cardCount(),
                                panel.damageCount(),
                                panel.disposition().value(),
                                panel.assessments().stream()
                                        .map(Schemas::assessmentOut)
                                        .toList()))
                        .toList());
    }

    /** The tail as it stood on the day, and its open cards, read in one unit of work. */
    private record Opened(TailContext tail, List<Finding> cards) {}
}
