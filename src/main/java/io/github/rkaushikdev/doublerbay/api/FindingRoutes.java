package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Geometry;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.service.Intake;
import io.github.rkaushikdev.doublerbay.service.Measuring;
import io.github.rkaushikdev.doublerbay.service.ReMeasured;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The routes that raise, read and re measure damage cards, and move them through their life.
 *
 * <p>Raising a card does two writes: the card and the disposition record that says where it is in
 * its life. They go in one unit of work, because a card with no disposition never appears on any
 * report and nobody would notice for weeks.
 *
 * <p>The reference is allocated by the service and not sent by the caller. Two mechanics filling in
 * a form at the same time will both write the next number they can see on the wall, and the desk
 * would rather refuse a duplicate here than find one in a fleet report six months later.
 */
@RestController
@Tag(name = "findings")
final class FindingRoutes {

    private final Deps deps;

    FindingRoutes(Deps deps) {
        this.deps = deps;
    }

    @PostMapping("/findings")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Action.RAISE_CARD)
    @Operation(summary = "Raise a damage card, with everything else raising one commits the desk to.")
    RaisedOut raiseFinding(@Valid @RequestBody FindingIn body) {
        Intake taken = deps.apply(unit -> {
            FindingId reference = Intake.nextReference(unit, body.foundOn());
            return Intake.takeIn(unit, deps.book(), deps.catalogue(), body.toDomain(reference), body.urgent());
        });
        return new RaisedOut(
                taken.reference().toString(),
                DispositionState.RAISED.value(),
                taken.graded().map(CorrosionEntry::level).map(level -> level.value()),
                taken.mustBeReported());
    }

    @GetMapping("/findings/{reference}")
    @Operation(summary = "The latest revision of one card.")
    FindingOut readFinding(@PathVariable("reference") String reference) {
        return deps.apply(unit -> Schemas.findingOut(unit.findings().latest(Dependencies.referenceFrom(reference))));
    }

    @GetMapping("/findings/{reference}/history")
    @Operation(summary = "Every revision of one card, oldest first.")
    List<FindingOut> readHistory(@PathVariable("reference") String reference) {
        return deps.apply(unit -> unit.findings().history(Dependencies.referenceFrom(reference)).stream()
                .map(Schemas::findingOut)
                .toList());
    }

    @PostMapping("/findings/{reference}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Action.RAISE_CARD)
    @Operation(
            summary = "Record a re measurement, and say what the new numbers changed.",
            description = "The response carries both dispositions rather than just the card. A client that got the"
                    + " card back would have to re assess it to find out whether anything moved, and half of"
                    + " them would not.")
    ReMeasuredOut recordRevision(@PathVariable("reference") String reference, @Valid @RequestBody RevisionIn body) {
        FindingId held = Dependencies.referenceFrom(reference);
        return deps.apply(unit -> {
            Finding current = unit.findings().latest(held);
            Geometry geometry = Schemas.measurementsIn(current.kind(), body.measurements());
            ReMeasured answer = Measuring.reMeasure(
                    unit,
                    deps.book(),
                    deps.catalogue(),
                    held,
                    geometry,
                    body.foundOn(),
                    body.atUsage().toDomain());
            return new ReMeasuredOut(
                    Schemas.findingOut(answer.finding()),
                    answer.was().value(),
                    answer.now().value(),
                    answer.gotWorse(),
                    answer.stillDeferred(),
                    answer.outgrewItsDeferral());
        });
    }

    @PostMapping("/findings/{reference}/supersede")
    @Requires(Action.DISPOSITION)
    @Operation(summary = "Close a card because another one now carries the damage.")
    FindingOut supersede(@PathVariable("reference") String reference, @Valid @RequestBody SupersedeIn body) {
        return deps.apply(unit -> {
            FindingId replacement = Dependencies.referenceFrom(body.by());
            unit.findings().latest(replacement);
            Finding closed = unit.findings().supersede(Dependencies.referenceFrom(reference), replacement);
            DispositionRecord record = unit.dispositions().get(Dependencies.referenceFrom(reference));
            unit.dispositions()
                    .save(record.movedTo(
                            DispositionState.SUPERSEDED, deps.on(), body.byWhom(), "carried by " + body.by()));
            return Schemas.findingOut(closed);
        });
    }

    @GetMapping("/aircraft/{serial}/findings")
    @Operation(
            summary = "Every card on one tail, or only the ones the desk still owns.",
            description = "Paged, because a twenty year old aeroplane carries four figures of them and nothing"
                    + " renders that.")
    Page<FindingOut> onTail(
            @PathVariable("serial") String serial,
            @RequestParam(name = "open_only", defaultValue = "false") boolean openOnly,
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "limit", required = false) Integer limit) {
        Window asked = Paging.window(offset, limit);
        return deps.apply(unit -> {
            Serial held = Dependencies.tailOn(unit, serial);
            List<Finding> cards =
                    openOnly ? unit.findings().openOn(held) : unit.findings().onTail(held);
            return Paging.paged(cards, asked, Schemas::findingOut);
        });
    }

    @GetMapping("/findings-of-kind/{kind}")
    @Operation(summary = "Every card of one kind across the fleet.")
    List<FindingOut> ofKind(@PathVariable("kind") String kind) {
        return deps.apply(unit -> {
            DamageKind held = Schemas.readEnum(DamageKind.values(), DamageKind::value, kind, "kind of damage");
            return unit.findings().ofKind(held).stream()
                    .map(Schemas::findingOut)
                    .toList();
        });
    }

    @PostMapping("/findings/{reference}/state")
    @Requires(Action.DISPOSITION)
    @Operation(
            summary = "Move a card to another state, refusing anything the lifecycle forbids.",
            description = "Every other write moves the card as a side effect of something else, so this is the"
                    + " route for the plain act of saying \"I have looked this up and it is assessed\".")
    DispositionOut moveState(@PathVariable("reference") String reference, @Valid @RequestBody StateIn body) {
        FindingId held = Dependencies.referenceFrom(reference);
        return deps.apply(unit -> {
            unit.findings().latest(held);
            DispositionRecord record = unit.dispositions().get(held);
            DispositionRecord moved = record.movedTo(
                    Schemas.readEnum(
                            DispositionState.values(), DispositionState::value, body.state(), "disposition state"),
                    body.on(),
                    body.by(),
                    body.note());
            unit.dispositions().save(moved);
            return dispositionOut(moved);
        });
    }

    @GetMapping("/findings/{reference}/state")
    @Operation(summary = "Where a card is now, and everything it has been through.")
    DispositionOut readState(@PathVariable("reference") String reference) {
        DispositionRecord record = deps.apply(unit -> unit.dispositions().get(Dependencies.referenceFrom(reference)));
        return dispositionOut(record);
    }

    private static DispositionOut dispositionOut(DispositionRecord record) {
        return new DispositionOut(
                record.reference().toString(),
                record.state().value(),
                record.isOpen(),
                record.history().stream().map(Object::toString).toList());
    }
}
