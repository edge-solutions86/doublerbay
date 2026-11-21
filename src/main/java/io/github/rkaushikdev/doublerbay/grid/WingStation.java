package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Distance outboard of the aircraft centreline, along the wing reference line: {@code WS 240.500}.
 *
 * <p>Wing stations run outboard from the aircraft centreline, which means the side of body is not
 * zero and the numbers on the left and the right wing are the same numbers. The hand is carried
 * alongside and never inferred from the station.
 */
public final class WingStation extends Axis {

    private static final Pattern SHAPE = Pattern.compile("(?:WS\\s*)?([0-9.]+)", Pattern.CASE_INSENSITIVE);

    /** A wing station at a distance outboard of the centreline. */
    public WingStation(Length at) {
        super("WS", at);
    }

    /** A wing station written in decimal inches. */
    public static WingStation ofInches(BigDecimal value) {
        return new WingStation(Length.ofInches(value));
    }

    /** A wing station written in decimal inches, as text. */
    public static WingStation ofInches(String value) {
        return new WingStation(Length.ofInches(value));
    }

    /** A wing station a whole number of inches outboard. */
    public static WingStation ofInches(long value) {
        return new WingStation(Length.ofInches(value));
    }

    /** Read a wing station with or without its letters: {@code WS 240.5}, {@code 240.5}. */
    public static WingStation parse(String text) {
        Matcher match = SHAPE.matcher(Text.strip(text));
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not a wing station");
        }
        BigDecimal value;
        try {
            value = Decimals.parse(match.group(1));
        } catch (NumberFormatException unreadable) {
            throw new BadReading(Literal.of(text) + " is not a wing station");
        }
        return new WingStation(Length.ofInches(value));
    }

    @Override
    public WingStation offset(Length distance) {
        return new WingStation(at().plus(distance));
    }
}
