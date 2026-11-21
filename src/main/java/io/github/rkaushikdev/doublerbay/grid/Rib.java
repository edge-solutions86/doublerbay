package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A chordwise former, numbered outboard from the side of body, on one wing.
 *
 * <p>A wing box is bounded fore and aft by spars and divided along its span by ribs, and the panel
 * between them is the wing equivalent of a bay.
 */
public record Rib(int number, Side side) implements WingMember, Comparable<Rib> {

    private static final Pattern SHAPE =
            Pattern.compile("RIB\\s*-?\\s*([0-9]{1,2})\\s*([LR])", Pattern.CASE_INSENSITIVE);

    private static final Comparator<Rib> ORDER =
            Comparator.comparing((Rib rib) -> rib.side().value()).thenComparingInt(Rib::number);

    /** A rib from one to forty on the left or the right wing. */
    public Rib {
        Objects.requireNonNull(side, "side");
        if (number < 1 || number > 40) {
            throw new BadReading("rib " + number + " is not on this wing");
        }
        if (side == Side.CENTRE) {
            throw new BadReading("a rib belongs to one wing or the other");
        }
    }

    /** Read the written form: {@code RIB12L}, {@code rib-12r}. */
    public static Rib parse(String text) {
        Matcher match = SHAPE.matcher(Text.strip(text));
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not a rib");
        }
        Side hand = Text.upper(match.group(2)).equals("L") ? Side.LEFT : Side.RIGHT;
        return new Rib(Integer.parseInt(match.group(1)), hand);
    }

    /** Whether this rib is inboard of another on the same wing. */
    public boolean isInboardOf(Rib other) {
        if (side != other.side) {
            throw new BadReading("ribs on opposite wings do not compare");
        }
        return number < other.number;
    }

    /** The same rib on the other wing. */
    public Rib mirrored() {
        return new Rib(number, side == Side.LEFT ? Side.RIGHT : Side.LEFT);
    }

    /** By wing, then by number. */
    @Override
    public int compareTo(Rib other) {
        return ORDER.compare(this, other);
    }

    /** {@code RIB12L}. */
    @Override
    public String toString() {
        return "RIB" + number + side;
    }
}
