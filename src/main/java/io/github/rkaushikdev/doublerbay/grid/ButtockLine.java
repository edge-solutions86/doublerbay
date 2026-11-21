package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Distance outboard of the centreline, negative to the left: {@code LBL 42.000}. */
public final class ButtockLine extends Axis {

    private static final Pattern SHAPE = Pattern.compile("(?:([LR])?BL\\s*)?([+-]?[0-9.]+)", Pattern.CASE_INSENSITIVE);

    /** A buttock line at a signed distance from the centreline, negative to the left. */
    public ButtockLine(Length at) {
        super("BL", at);
    }

    /** A buttock line written in signed decimal inches. */
    public static ButtockLine ofInches(BigDecimal value) {
        return new ButtockLine(Length.ofInches(value));
    }

    /** A buttock line written in signed decimal inches, as text. */
    public static ButtockLine ofInches(String value) {
        return new ButtockLine(Length.ofInches(value));
    }

    /** A buttock line a whole number of inches from the centreline. */
    public static ButtockLine ofInches(long value) {
        return new ButtockLine(Length.ofInches(value));
    }

    /**
     * Read a buttock line: {@code LBL 42}, {@code RBL 42}, {@code BL 0} or a signed reading. A hand
     * written in front wins over the sign that was typed after it.
     */
    public static ButtockLine parse(String text) {
        Matcher match = SHAPE.matcher(Text.strip(text));
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not a buttock line");
        }
        String hand = match.group(1);
        Length reading = inches(match.group(2), "buttock line");
        if (hand != null && Text.upper(hand).equals("L")) {
            reading = reading.abs().negate();
        } else if (hand != null && Text.upper(hand).equals("R")) {
            reading = reading.abs();
        }
        return new ButtockLine(reading);
    }

    /** Whether the reading is left of the centreline. */
    public boolean isLeft() {
        return at().mils() < 0;
    }

    /** Whether the reading is on the centreline. */
    public boolean isCentreline() {
        return at().mils() == 0;
    }

    /** The same distance on the other side of the centreline. */
    public ButtockLine mirrored() {
        return new ButtockLine(at().negate());
    }

    @Override
    public ButtockLine offset(Length distance) {
        return new ButtockLine(at().plus(distance));
    }

    /** {@code LBL 42.000}, {@code RBL 42.000}, or {@code BL 0.000} on the centreline. */
    @Override
    public String toString() {
        if (isCentreline()) {
            return "BL 0.000";
        }
        String hand = isLeft() ? "L" : "R";
        return hand + "BL " + at().abs().inches().toPlainString();
    }
}
