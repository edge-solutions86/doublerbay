package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A ring former, identified by its number and located by the type definition.
 *
 * <p>Almost every limit in the manual is written per bay, because a bay is what carries the load
 * between its edges and what a crack has to cross before it reaches something that will stop it,
 * and frames are what bound a bay fore and aft.
 */
public record Frame(int number) implements JointMember, Comparable<Frame> {

    private static final Pattern SHAPE = Pattern.compile("(?:FR|FS)\\s*-?\\s*([0-9]{1,3})", Pattern.CASE_INSENSITIVE);

    /** A frame numbered from one. */
    public Frame {
        if (number < 1) {
            throw new BadReading("frame numbers start at one, got " + number);
        }
    }

    /** Read the written form: {@code FR42}, {@code FS 42}. */
    public static Frame parse(String text) {
        Matcher match = SHAPE.matcher(Text.strip(text));
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not a frame");
        }
        return new Frame(Integer.parseInt(match.group(1)));
    }

    /** The next frame aft. */
    public Frame nextAft() {
        return new Frame(number + 1);
    }

    /** How many frame pitches apart. */
    public int spansTo(Frame other) {
        return Math.abs(number - other.number);
    }

    @Override
    public int compareTo(Frame other) {
        return Integer.compare(number, other.number);
    }

    /** Whether this frame is forward of another. */
    public boolean isLessThan(Frame other) {
        return number < other.number;
    }

    /** Whether this frame is no further aft than another. */
    public boolean isAtMost(Frame other) {
        return number <= other.number;
    }

    /** Whether this frame is aft of another. */
    public boolean isGreaterThan(Frame other) {
        return number > other.number;
    }

    /** Whether this frame is no further forward than another. */
    public boolean isAtLeast(Frame other) {
        return number >= other.number;
    }

    /** {@code FR42}. */
    @Override
    public String toString() {
        return "FR" + number;
    }
}
