package io.github.rkaushikdev.doublerbay.repair;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.structure.DoublerForm;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import io.github.rkaushikdev.doublerbay.structure.InstalledDoubler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The repair as a record, which is what the next repair in that area has to work around.
 *
 * <p>A repair is not finished when the last rivet goes in. It becomes a permanent feature of the
 * aeroplane, and every assessment after it has to know it is there: what skin it covers, how thick it
 * made that area, when it went on, and what it committed the operator to. A repair nobody recorded is
 * a repair the next engineer sizes a doubler straight over the top of.
 *
 * <p>Clearance is the rule that bites. Two repairs whose fastener patterns come inside a few diameters
 * of each other are feeding load into the same holes, and neither one was sized for that. The
 * clearance is measured between the patches and not between the damages they cover, which is a bigger
 * number than people expect once both skirts are counted.
 */
public record RepairRecord(
        RepairId reference,
        PanelId part,
        FindingId against,
        RepairScheme scheme,
        Offset centre,
        LocalDate embodied,
        Usage atUsage,
        Optional<LocalDate> removed) {

    /**
     * Two repairs closer than this are sharing load paths neither was sized for. Stated in fastener
     * diameters of the larger of the two patterns.
     */
    public static final BigDecimal CLEARANCE_DIAMETERS = new BigDecimal("4.0");

    /** A repair that did not come off before it went on. */
    public RepairRecord {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(against, "against");
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(centre, "centre");
        Objects.requireNonNull(embodied, "embodied");
        Objects.requireNonNull(atUsage, "atUsage");
        Objects.requireNonNull(removed, "removed");
        if (removed.isPresent() && removed.get().isBefore(embodied)) {
            throw new BadReading(reference + " was removed before it went on");
        }
    }

    /** A repair still fitted. */
    public RepairRecord(
            RepairId reference,
            PanelId part,
            FindingId against,
            RepairScheme scheme,
            Offset centre,
            LocalDate embodied,
            Usage atUsage) {
        this(reference, part, against, scheme, centre, embodied, atUsage, Optional.empty());
    }

    /** A repair from what was to hand; a null removal date is a repair still fitted. */
    public static RepairRecord of(
            RepairId reference,
            PanelId part,
            FindingId against,
            RepairScheme scheme,
            Offset centre,
            LocalDate embodied,
            Usage atUsage,
            LocalDate removed) {
        return new RepairRecord(
                reference, part, against, scheme, centre, embodied, atUsage, Optional.ofNullable(removed));
    }

    /** What the repair committed the operator to. */
    public RepairCategory category() {
        return scheme.category();
    }

    /** Whether the repair is still on the aeroplane. */
    public boolean isFitted() {
        return removed.isEmpty();
    }

    /** Whether the repair is on and its category carries an inspection. */
    public boolean needsInspection() {
        return isFitted() && scheme.needsInspection();
    }

    /** The skin the patch covers, which is the doubler and not the damage. */
    public Footprint footprint() {
        return Footprint.square(
                centre, scheme.doubler().along(), scheme.doubler().around());
    }

    /** How far another patch has to stay off this one, off its fastener size. */
    public Length requiredClearance() {
        return scheme.doubler().size().diameters(CLEARANCE_DIAMETERS);
    }

    /** The record as the structure definition wants to see it. */
    public InstalledDoubler asInstalled() {
        DoublerForm form =
                switch (scheme.form()) {
                    case EXTERNAL -> DoublerForm.EXTERNAL;
                    case FLUSH -> DoublerForm.FLUSH;
                    case INTERNAL -> DoublerForm.INTERNAL;
                };
        return new InstalledDoubler(
                reference.toString(),
                form,
                footprint(),
                scheme.doubler().material(),
                scheme.doubler().gauge(),
                embodied);
    }

    /** Whether the two patches are far enough apart to act independently; the larger clearance governs. */
    public boolean clears(RepairRecord other) {
        Length needed = Length.max(requiredClearance(), other.requiredClearance());
        return footprint().clears(other.footprint(), needed);
    }

    /** The gap between the two patches, zero if they touch. */
    public Length clearanceTo(RepairRecord other) {
        return footprint().clearanceTo(other.footprint());
    }

    /** The record after the repair came off on a day. The record it came from is left as it was. */
    public RepairRecord removedOn(LocalDate day) {
        return new RepairRecord(reference, part, against, scheme, centre, embodied, atUsage, Optional.of(day));
    }

    /** Every pair of fitted repairs that is inside the clearance the other needs, in the order given. */
    public static List<Clash> clashes(List<RepairRecord> fitted) {
        List<Clash> found = new ArrayList<>();
        List<RepairRecord> live = fitted.stream().filter(RepairRecord::isFitted).toList();
        for (int index = 0; index < live.size(); index++) {
            RepairRecord first = live.get(index);
            for (RepairRecord second : live.subList(index + 1, live.size())) {
                if (!first.clears(second)) {
                    found.add(new Clash(first, second));
                }
            }
        }
        return List.copyOf(found);
    }

    /** {@code RP-53-0001 on 5310-143L, category A, fitted}. */
    @Override
    public String toString() {
        String state = isFitted() ? "fitted" : "removed";
        return reference + " on " + part + ", " + scheme.category() + ", " + state;
    }
}
