package io.github.rkaushikdev.doublerbay.ids;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The manufacturer serial number, which the airframe keeps for life.
 *
 * <p>Everything about an aeroplane is keyed on this rather than on its {@link Registration},
 * because mixing the two is how a repair ends up filed against somebody else's aircraft.
 */
public record Serial(String number) implements Comparable<Serial> {

    private static final Pattern SHAPE = Pattern.compile("[0-9]{3,5}");

    /** A serial of three to five digits. */
    public Serial {
        Objects.requireNonNull(number, "number");
        if (!SHAPE.matcher(number).matches()) {
            throw new BadReading(Literal.of(number) + " is not a serial number");
        }
    }

    /** A serial as written on a form, trimmed. */
    public static Serial parse(String text) {
        return new Serial(Text.strip(text));
    }

    /** The serial as a number. */
    public int asInt() {
        return Integer.parseInt(number);
    }

    /** Serials order as the text they are stored as. */
    @Override
    public int compareTo(Serial other) {
        return number.compareTo(other.number);
    }

    /** {@code MSN 5127}, the way the serial is printed on every document. */
    @Override
    public String toString() {
        return "MSN " + number;
    }
}
