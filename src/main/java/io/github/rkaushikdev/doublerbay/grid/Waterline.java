package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Height above the vertical datum: {@code WL 208.000}. */
public final class Waterline extends Axis {

    private static final Pattern SHAPE = Pattern.compile("(?:WL\\s*)?([+-]?[0-9.]+)", Pattern.CASE_INSENSITIVE);

    /** A waterline at a height above the datum. */
    public Waterline(Length at) {
        super("WL", at);
    }

    /** A waterline written in decimal inches. */
    public static Waterline ofInches(BigDecimal value) {
        return new Waterline(Length.ofInches(value));
    }

    /** A waterline written in decimal inches, as text. */
    public static Waterline ofInches(String value) {
        return new Waterline(Length.ofInches(value));
    }

    /** A waterline a whole number of inches above the datum. */
    public static Waterline ofInches(long value) {
        return new Waterline(Length.ofInches(value));
    }

    /** Read a waterline with or without its letters: {@code WL 208}, {@code 208}. */
    public static Waterline parse(String text) {
        Matcher match = SHAPE.matcher(Text.strip(text));
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not a waterline");
        }
        return new Waterline(inches(match.group(1), "waterline"));
    }

    @Override
    public Waterline offset(Length distance) {
        return new Waterline(at().plus(distance));
    }
}
