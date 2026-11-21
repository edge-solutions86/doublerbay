package io.github.rkaushikdev.doublerbay.measure;

import io.github.rkaushikdev.doublerbay.BadReading;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One rung of the stock ladder, with the spread the mill is allowed.
 *
 * <p>A repair doubler is cut from stock, and stock comes in a fixed ladder of nominal gauges. When
 * the assessment says a damage needs a 0.047 doubler the shop does not have 0.047, it has 0.050, so
 * anything that sizes a part lands on the ladder rather than on the arithmetic. The mill tolerance
 * matters for a different reason: a skin called out at 0.040 can leave the mill at 0.0365 and still
 * be in spec, so a thickness measured off the aircraft is compared against the low side of the
 * callout and never against the callout itself.
 */
public record SheetGauge(Length nominal, Length under, Length over) {

    /**
     * The clad aluminium sheet the V320 skin and its repair parts are cut from. Tolerance widens
     * with the gauge, which is why it is carried per rung and not as one percentage.
     */
    public static final List<SheetGauge> STOCK_LADDER = List.of(
            rung("0.016", "0.002", "0.002"),
            rung("0.020", "0.002", "0.002"),
            rung("0.025", "0.002", "0.002"),
            rung("0.032", "0.003", "0.003"),
            rung("0.040", "0.004", "0.004"),
            rung("0.050", "0.004", "0.004"),
            rung("0.063", "0.005", "0.005"),
            rung("0.071", "0.005", "0.005"),
            rung("0.080", "0.006", "0.006"),
            rung("0.090", "0.006", "0.006"),
            rung("0.100", "0.007", "0.007"),
            rung("0.125", "0.008", "0.008"),
            rung("0.160", "0.010", "0.010"),
            rung("0.190", "0.012", "0.012"),
            rung("0.250", "0.015", "0.015"));

    private static final Map<Length, SheetGauge> BY_NOMINAL = byNominal();

    /** A rung with its nominal and both sides of its spread. */
    public SheetGauge {
        Objects.requireNonNull(nominal, "nominal");
        Objects.requireNonNull(under, "under");
        Objects.requireNonNull(over, "over");
    }

    private static SheetGauge rung(String nominal, String under, String over) {
        return new SheetGauge(Length.ofInches(nominal), Length.ofInches(under), Length.ofInches(over));
    }

    private static Map<Length, SheetGauge> byNominal() {
        Map<Length, SheetGauge> held = new LinkedHashMap<>();
        for (SheetGauge rung : STOCK_LADDER) {
            held.put(rung.nominal(), rung);
        }
        return Map.copyOf(held);
    }

    /** The rung called out by this exact nominal thickness. */
    public static SheetGauge gaugeFor(Length nominal) {
        SheetGauge rung = BY_NOMINAL.get(nominal);
        if (rung == null) {
            throw new BadReading(nominal + " is not a stocked gauge");
        }
        return rung;
    }

    /** Whether a nominal thickness is a rung on the ladder. */
    public static boolean isStocked(Length nominal) {
        return BY_NOMINAL.containsKey(nominal);
    }

    /**
     * The thinnest rung that is at least as thick as what was asked for. Sizing rounds up: a
     * doubler thinner than the calculation is not a doubler.
     */
    public static SheetGauge nextHeavier(Length wanted) {
        for (SheetGauge rung : STOCK_LADDER) {
            if (rung.nominal().isAtLeast(wanted)) {
                return rung;
            }
        }
        throw new BadReading(wanted + " is heavier than any stocked sheet");
    }

    /**
     * The next rung strictly above a gauge, which is what a doubler is cut from. Not the same as
     * {@link #nextHeavier}, which hands back the gauge itself when it is already on the ladder: a
     * doubler the same gauge as the skin it covers is not a doubler.
     */
    public static SheetGauge oneStepHeavier(Length nominal) {
        for (SheetGauge rung : STOCK_LADDER) {
            if (rung.nominal().isGreaterThan(nominal)) {
                return rung;
            }
        }
        throw new BadReading(nominal + " is the heaviest stocked sheet");
    }

    /** The rung closest to what was asked for, breaking a tie upwards. */
    public static SheetGauge nearest(Length wanted) {
        SheetGauge best = STOCK_LADDER.get(0);
        Length bestGap = best.nominal().minus(wanted).abs();
        for (SheetGauge rung : STOCK_LADDER.subList(1, STOCK_LADDER.size())) {
            Length gap = rung.nominal().minus(wanted).abs();
            if (gap.isLessThan(bestGap)
                    || (gap.equals(bestGap) && rung.nominal().isGreaterThan(best.nominal()))) {
                best = rung;
                bestGap = gap;
            }
        }
        return best;
    }

    /**
     * Which callout a measured thickness could have come off. Ambiguous by construction once the
     * tolerance bands touch, so this answers only when exactly one rung admits the reading.
     */
    public static Optional<SheetGauge> identify(Length measured) {
        List<SheetGauge> admitting =
                STOCK_LADDER.stream().filter(rung -> rung.admits(measured)).toList();
        return admitting.size() == 1 ? Optional.of(admitting.get(0)) : Optional.empty();
    }

    /** The callout the rung is known by, {@code 0.040}. */
    public String designation() {
        return nominal.inches().toPlainString();
    }

    /** The thinnest sheet the mill may send for this rung. */
    public Length floor() {
        return nominal.minus(under);
    }

    /** The thickest sheet the mill may send for this rung. */
    public Length ceiling() {
        return nominal.plus(over);
    }

    /** Whether a measured thickness is inside the band, its edges included. */
    public boolean admits(Length measured) {
        return floor().isAtMost(measured) && measured.isAtMost(ceiling());
    }

    @Override
    public String toString() {
        return designation();
    }
}
