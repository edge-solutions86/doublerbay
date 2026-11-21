package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Bay;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Taper;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the skin actually measures where the damage is: land, pocket and the taper between them, plus
 * anything padded locally.
 *
 * <p>A panel has one part number and several thicknesses. It leaves the mill at full gauge and then
 * gets chemically milled away in the middle of every bay, leaving a full thickness land along the
 * members where the fasteners go and a thinner pocket in between. Between the two there is a taper,
 * because a chem mill step with a sharp corner is a stress raiser and would crack before anything
 * else on the panel did.
 *
 * <p>This is the reason a damage assessment cannot start from the panel drawing. A dent 0.020 deep
 * is a third of the way through a 0.063 land and half way through the 0.040 pocket two inches away
 * from it, and the limit table is keyed on the thickness at the damage. Read the wrong one and the
 * disposition is wrong in a direction nobody catches, because it clears rather than holds.
 */
public record ThicknessMap(Length land, Length pocket, Length landWidth, Taper step, List<Pad> pads) {

    /** The step the mills hold unless a drawing says otherwise. */
    public static final Taper STANDARD_STEP = new Taper(20);

    /** A map with a land at least as thick as its pocket, and every pad at least as thick as the land. */
    public ThicknessMap {
        Objects.requireNonNull(land, "land");
        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(landWidth, "landWidth");
        Objects.requireNonNull(step, "step");
        pads = List.copyOf(pads);
        if (land.mils() <= 0) {
            throw new BadReading("a land has a thickness");
        }
        if (pocket.mils() <= 0) {
            throw new BadReading("a pocket has a thickness");
        }
        if (pocket.isGreaterThan(land)) {
            throw new BadReading("pocket " + pocket + " is thicker than land " + land);
        }
        if (landWidth.mils() < 0) {
            throw new BadReading("a land width does not run backwards");
        }
        for (Pad pad : pads) {
            if (pad.gauge().isLessThan(land)) {
                throw new BadReading(pad.name() + " is thinner than the land it sits on");
            }
        }
    }

    /** A milled panel with the standard step and no pads. */
    public ThicknessMap(Length land, Length pocket, Length landWidth) {
        this(land, pocket, landWidth, STANDARD_STEP, List.of());
    }

    /** A milled panel with its own step and no pads. */
    public ThicknessMap(Length land, Length pocket, Length landWidth, Taper step) {
        this(land, pocket, landWidth, step, List.of());
    }

    /** A panel that was never milled, which is most of the tail cone. */
    public static ThicknessMap flat(Length gauge) {
        return new ThicknessMap(gauge, gauge, new Length(0));
    }

    /** Whether the panel is one gauge everywhere. */
    public boolean isFlat() {
        return land.equals(pocket);
    }

    /** How much the mill took off between the land and the pocket floor. */
    public Length stepDepth() {
        return land.minus(pocket);
    }

    /** How far in from the land the taper takes to reach the pocket floor. */
    public Length stepRun() {
        return step.runout(stepDepth());
    }

    /** How far in from the member the pocket floor starts. */
    public Length pocketStartsAt() {
        return landWidth.plus(stepRun());
    }

    /** The thickness this far in from the nearest bounding member, with no bay to look for a pad on. */
    public Length at(Length margin) {
        return at(margin, null);
    }

    /**
     * The thickness this far in from the nearest bounding member.
     *
     * <p>A pad on the bay overrides everything, because a pad is bonded or riveted on top of the
     * whole bay and the mill never touched it.
     *
     * @param bay the bay the point is in, or null when it is not known, which reads no pad
     */
    public Length at(Length margin, Bay bay) {
        if (margin.mils() < 0) {
            throw new BadReading("a margin is measured in from a member, not out");
        }
        Optional<Pad> padded = padOn(bay);
        if (padded.isPresent()) {
            return padded.get().gauge();
        }
        if (isFlat() || margin.isAtMost(landWidth)) {
            return land;
        }
        if (margin.isAtLeast(pocketStartsAt())) {
            return pocket;
        }
        Length intoTaper = margin.minus(landWidth);
        BigDecimal fallen = Decimals.over(BigDecimal.valueOf(intoTaper.mils()), step.run());
        return new Length(land.mils() - Decimals.toInt(fallen));
    }

    /** Whether a point is on the taper, where the manual will not allow a blend. */
    public boolean onTheStep(Length margin) {
        if (isFlat()) {
            return false;
        }
        return landWidth.isLessThan(margin) && margin.isLessThan(pocketStartsAt());
    }

    /**
     * The pad on a bay, if there is one.
     *
     * @param bay the bay to look on, or null when it is not known, which carries no pad
     */
    public Optional<Pad> padOn(Bay bay) {
        if (bay == null) {
            return Optional.empty();
        }
        for (Pad pad : pads) {
            if (pad.bay().equals(bay)) {
                return Optional.of(pad);
            }
        }
        return Optional.empty();
    }

    /** The same map with one more pad on it. */
    public ThicknessMap withPad(Pad pad) {
        List<Pad> more = new ArrayList<>(pads);
        more.add(pad);
        return new ThicknessMap(land, pocket, landWidth, step, more);
    }

    /** {@code 0.063 land / 0.040 pocket}, or {@code flat 0.050}. */
    @Override
    public String toString() {
        if (isFlat()) {
            return "flat " + land.inches().toPlainString();
        }
        return land.inches().toPlainString() + " land / " + pocket.inches().toPlainString() + " pocket";
    }
}
