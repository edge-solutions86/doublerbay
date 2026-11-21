package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Distance aft of the fuselage datum: {@code STA 727.500}. */
public final class Station extends Axis {

    private static final Pattern SHAPE = Pattern.compile("(?:STA\\s*)?([+-]?[0-9.]+)", Pattern.CASE_INSENSITIVE);

    /** A station at a distance aft of the datum. */
    public Station(Length at) {
        super("STA", at);
    }

    /** A station written in decimal inches. */
    public static Station ofInches(BigDecimal value) {
        return new Station(Length.ofInches(value));
    }

    /** A station written in decimal inches, as text. */
    public static Station ofInches(String value) {
        return new Station(Length.ofInches(value));
    }

    /** A station a whole number of inches aft of the datum. */
    public static Station ofInches(long value) {
        return new Station(Length.ofInches(value));
    }

    /** Read a station with or without its letters: {@code STA 727.5}, {@code 727.5}. */
    public static Station parse(String text) {
        Matcher match = SHAPE.matcher(Text.strip(text));
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not a station");
        }
        return new Station(inches(match.group(1), "station"));
    }

    @Override
    public Station offset(Length distance) {
        return new Station(at().plus(distance));
    }
}
