package io.github.rkaushikdev.doublerbay.tolerance;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.repair.RepairCategory;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Where a repeat interval comes from, which is a crack growth argument and not a guess.
 *
 * <p>A repair that needs inspecting needs a number, and the number is not picked. It falls out of
 * three things: how long a crack can get before the structure stops carrying its load, how small a
 * crack the chosen method can be relied on to find, and how fast a crack grows there. The flying
 * between those two lengths is the whole opportunity to catch it, and the interval is that divided by
 * a safety factor so the crack gets looked at at least twice on its way up.
 *
 * <p>Which is why a more sensitive method buys a longer interval. Eddy current finds a crack at fifty
 * thousandths where a detailed visual needs half an inch, and those extra four hundred and fifty
 * thousandths of growth are months of flying. The expensive inspection is often the cheaper programme.
 *
 * <p>The threshold is the flying before the first one. A crack has to start before it can grow, and
 * initiation takes longer than growth does, so the threshold is a multiple of the repeat rather than
 * the same number.
 */
public final class Schedules {

    /** A crack is looked at at least twice between being findable and being critical. */
    public static final int SAFETY_FACTOR = 2;

    /** Initiation takes longer than growth, so the first look is further out. */
    public static final int THRESHOLD_FACTOR = 2;

    /** How long a temporary repair may stay on before it is replaced properly, in months. */
    public static final int TEMPORARY_LIMIT_MONTHS = 12;

    /**
     * Growth rates by the kind of skin the repair is on. The lower wing surface is the fast one because
     * it spends the flight in tension.
     */
    public static final Map<PanelKind, GrowthBasis> BASIS = bases();

    private Schedules() {}

    private static Map<PanelKind, GrowthBasis> bases() {
        Map<PanelKind, GrowthBasis> held = new LinkedHashMap<>();
        held.put(PanelKind.PRESSURISED_SKIN, basis("6.000", "0.100"));
        held.put(PanelKind.CROWN_SKIN, basis("6.000", "0.080"));
        held.put(PanelKind.BELLY_SKIN, basis("6.000", "0.090"));
        held.put(PanelKind.UNPRESSURISED_SKIN, basis("8.000", "0.040"));
        held.put(PanelKind.WING_UPPER, basis("6.000", "0.050"));
        held.put(PanelKind.WING_LOWER, basis("4.000", "0.150"));
        return Collections.unmodifiableMap(held);
    }

    private static GrowthBasis basis(String critical, String growthPerThousand) {
        return new GrowthBasis(Length.ofInches(critical), Length.ofInches(growthPerThousand));
    }

    /**
     * What a crack does in a kind of skin.
     *
     * @throws NotOnFile when there is no crack growth data for the kind
     */
    public static GrowthBasis basisFor(PanelKind kind) {
        GrowthBasis basis = BASIS.get(kind);
        if (basis == null) {
            throw new NotOnFile("no crack growth data on file for " + kind);
        }
        return basis;
    }

    /**
     * Work out the inspection programme a repair of this category carries, none for a permanent repair.
     *
     * <p>A temporary repair gets a replacement limit and no repeat. An inspected repair gets a repeat of
     * the growth life from what the method finds over the safety factor, and a threshold of the repeat
     * times the threshold factor.
     *
     * @throws BadReading when the method leaves no room for a repeat on the kind of skin
     */
    public static Optional<InspectionSchedule> scheduleFor(
            RepairCategory category, PanelKind kind, InspectionMethod method) {
        if (!category.needsInspection()) {
            return Optional.empty();
        }
        if (category == RepairCategory.C) {
            return Optional.of(InspectionSchedule.of(method, Interval.ofMonths(TEMPORARY_LIMIT_MONTHS), null));
        }
        Cycles growth = basisFor(kind).growthCycles(method.findsDownTo());
        Cycles repeat = new Cycles(Math.floorDiv(growth.count(), SAFETY_FACTOR));
        if (repeat.count() < 1) {
            throw new BadReading(method + " leaves no room for a repeat interval on " + kind);
        }
        return Optional.of(InspectionSchedule.of(
                method, Interval.ofCycles(new Cycles(repeat.count() * THRESHOLD_FACTOR)), Interval.ofCycles(repeat)));
    }
}
