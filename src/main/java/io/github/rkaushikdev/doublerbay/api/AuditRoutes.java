package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.report.StructureAudit;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The structure audit routes, over the fleet and over one tail.
 *
 * <p>The fleet route is the one that gets opened once a quarter and then argued about, so it comes
 * back sorted worst first and carries the counts that decide the argument rather than the whole
 * picture. Somebody who wants the whole picture asks for the tail.
 *
 * <p>Nothing here writes. An audit that could change a record would be an audit somebody could make
 * come out right.
 */
@RestController
@Tag(name = "audit")
final class AuditRoutes {

    private final Deps deps;

    AuditRoutes(Deps deps) {
        this.deps = deps;
    }

    @GetMapping("/audit/{serial}")
    @Operation(summary = "What one aeroplane's structure is carrying.")
    AuditOut tailAudit(
            @PathVariable("serial") String serial,
            @RequestParam(name = "on") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        return deps.apply(unit -> auditOut(auditTail(unit, Dependencies.tailOn(unit, serial), on)));
    }

    @GetMapping("/audit")
    @Operation(summary = "The same picture across the register, worst first.")
    FleetAuditOut fleetAudit(@RequestParam(name = "on") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        List<StructureAudit> built = new ArrayList<>(deps.apply(unit -> {
            List<StructureAudit> audits = new ArrayList<>();
            for (Aircraft one : unit.aircraft().inServiceOn(on)) {
                audits.add(auditTail(unit, one.serial(), on));
            }
            return audits;
        }));
        built.sort(
                Comparator.comparingInt((StructureAudit one) -> -one.clashing().size())
                        .thenComparingInt(one -> -one.overdue().size())
                        .thenComparingInt(one -> -one.crowded().size())
                        .thenComparingInt(one -> -one.totalRepairs())
                        .thenComparing(one -> one.serial().number()));
        int flagged = (int) built.stream().filter(one -> !one.isClean()).count();
        return new FleetAuditOut(on, built.stream().map(AuditRoutes::lineOut).toList(), flagged);
    }

    /** One tail's audit, off the repairs still fitted to it. */
    static StructureAudit auditTail(UnitOfWork unit, Serial serial, LocalDate on) {
        return StructureAudit.audit(serial, unit.repairs().onTail(serial), on);
    }

    /** One tail's whole picture as it goes back. */
    static AuditOut auditOut(StructureAudit built) {
        return new AuditOut(
                built.serial().number(),
                built.on(),
                built.totalRepairs(),
                built.isClean(),
                built.panels().stream()
                        .map(one -> new PanelLoadOut(
                                one.part().toString(), one.repairs(), one.temporary(), one.isCrowded()))
                        .toList(),
                built.temporary().stream()
                        .map(one -> new TemporaryOut(
                                one.reference().toString(),
                                one.part().toString(),
                                one.embodied(),
                                Decimals.text(one.monthsFitted()),
                                one.isOverdue()))
                        .toList(),
                built.clashing().stream()
                        .map(pair -> List.of(pair.one().toString(), pair.other().toString()))
                        .toList());
    }

    /** One tail's line on the fleet audit. */
    static FleetLineOut lineOut(StructureAudit built) {
        return new FleetLineOut(
                built.serial().number(),
                built.totalRepairs(),
                built.crowded().size(),
                built.overdue().size(),
                built.clashing().size(),
                built.isClean());
    }
}
