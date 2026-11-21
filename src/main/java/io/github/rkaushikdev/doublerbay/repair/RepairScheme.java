package io.github.rkaushikdev.doublerbay.repair;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.Objects;
import java.util.Optional;

/**
 * One repair, as it would be drawn: its form, its category, the doubler, whether it is sealed, the
 * manual page it was schemed against, and the filler a flush repair needs to make up the skin line.
 *
 * <p>{@link Schemes#selectScheme} works one out for a hole at a place.
 */
public record RepairScheme(
        RepairForm form,
        RepairCategory category,
        DoublerPlan doubler,
        boolean sealed,
        Reference reference,
        Optional<Length> filler) {

    /** A scheme whose filler has a thickness, and a flush scheme that has one. */
    public RepairScheme {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(doubler, "doubler");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(filler, "filler");
        if (filler.isPresent() && filler.get().mils() <= 0) {
            throw new BadReading("a filler has a thickness");
        }
        if (form == RepairForm.FLUSH && filler.isEmpty()) {
            throw new BadReading("a flush repair needs a filler to make up the skin line");
        }
    }

    /** A scheme with no filler, which is any scheme but a flush one. */
    public RepairScheme(
            RepairForm form, RepairCategory category, DoublerPlan doubler, boolean sealed, Reference reference) {
        this(form, category, doubler, sealed, reference, Optional.empty());
    }

    /** A scheme from what was to hand; a null filler is a scheme that has none. */
    public static RepairScheme of(
            RepairForm form,
            RepairCategory category,
            DoublerPlan doubler,
            boolean sealed,
            Reference reference,
            Length filler) {
        return new RepairScheme(form, category, doubler, sealed, reference, Optional.ofNullable(filler));
    }

    /** Whether the repair carries an inspection, which its category decides. */
    public boolean needsInspection() {
        return category.needsInspection();
    }

    /** Every hole in the doubler. */
    public int fastenerCount() {
        return doubler.fastenerCount();
    }

    /**
     * {@code external doubler, category A, 0.050 2024-T3 CLAD, 5.282 by 5.282, 48 off -5, sealed}, the
     * last part only when it is sealed.
     */
    @Override
    public String toString() {
        String seal = sealed ? ", sealed" : "";
        return form + ", " + category + ", " + doubler + seal;
    }
}
