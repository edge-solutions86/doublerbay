package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * The growth between two consecutive readings of one card.
 *
 * @see Growth
 */
public record Step(Length was, Length now, int cycles) {

    private static final int RATE_PLACES = 4;
    private static final BigDecimal MILS_PER_INCH = BigDecimal.valueOf(Length.MILS_PER_INCH);

    /** Two readings and the cycles flown between them, which do not run backwards. */
    public Step {
        Objects.requireNonNull(was, "was");
        Objects.requireNonNull(now, "now");
        if (cycles < 0) {
            throw new BadReading("a growth step does not run backwards in cycles");
        }
    }

    /** How much longer it got, which is negative for one that measured shorter. */
    public Length grewBy() {
        return new Length(now.mils() - was.mils());
    }

    /** Whether enough flying happened between the readings to mean anything. */
    public boolean isMeasurable() {
        return cycles >= Growth.SHORTEST_SPAN;
    }

    /**
     * Inches per thousand cycles, to four places with a half rounding up.
     *
     * @throws BadReading when the span is too short to measure a rate over
     */
    public BigDecimal rate() {
        return unroundedRate().setScale(RATE_PLACES, RoundingMode.HALF_UP);
    }

    /**
     * The rate as the desk prints it, to four places. A crack that measured shorter by too little to
     * show at four places still reads as shrinking: {@code -0.0000}, not {@code 0.0000}.
     *
     * @throws BadReading when the span is too short to measure a rate over
     */
    public String rateText() {
        BigDecimal unrounded = unroundedRate();
        BigDecimal rounded = unrounded.setScale(RATE_PLACES, RoundingMode.HALF_UP);
        if (rounded.signum() == 0 && unrounded.signum() < 0) {
            return "-" + rounded.toPlainString();
        }
        return Decimals.text(rounded);
    }

    private BigDecimal unroundedRate() {
        if (!isMeasurable()) {
            throw new BadReading(cycles + " cycles is too short a span to take a rate from");
        }
        BigDecimal per = Decimals.over(Decimals.times(BigDecimal.valueOf(grewBy().mils()), Growth.PER_CYCLES), cycles);
        return Decimals.over(per, MILS_PER_INCH);
    }

    /** {@code 0.400 to 0.500 over 1000 FC}. */
    @Override
    public String toString() {
        return was.inches().toPlainString() + " to " + now.inches().toPlainString() + " over " + cycles + " FC";
    }
}
