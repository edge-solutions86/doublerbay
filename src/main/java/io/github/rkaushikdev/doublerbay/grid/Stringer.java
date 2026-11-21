package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A longitudinal stiffener, numbered down from the crown on one hand.
 *
 * <p>The barrel is a lattice. Frames run around it at fixed stations and stringers run along it at
 * fixed positions around the circumference, and the square of skin between two of each is a bay.
 * Stringers count outwards from the crown, S-1 immediately off the top centreline and climbing as
 * they come down the side, so S-4 is above S-22 on the aircraft and below it in the number. Getting
 * that inverted puts damage on the wrong half of the barrel, so the ordering here is explicit and
 * tested rather than left to the integer.
 */
public record Stringer(int number, Side side) implements JointMember, Comparable<Stringer> {

    /** The crown stringer's number, on the top centreline. */
    public static final int CROWN = 0;

    /** The highest stringer number on the barrel. */
    public static final int MAX_STRINGER = 48;

    private static final Pattern SHAPE = Pattern.compile("S-?([0-9]{1,2})\\s*([LR])?", Pattern.CASE_INSENSITIVE);

    private static final Comparator<Stringer> ORDER = Comparator.comparingInt(Stringer::number)
            .thenComparing(stringer -> stringer.side().value());

    /** A stringer on the barrel, handed unless it is the crown. */
    public Stringer {
        Objects.requireNonNull(side, "side");
        if (number < CROWN || number > MAX_STRINGER) {
            throw new BadReading("S-" + number + " is not on this barrel");
        }
        if (number == CROWN && side != Side.CENTRE) {
            throw new BadReading("the crown stringer has no hand");
        }
        if (number != CROWN && side == Side.CENTRE) {
            throw new BadReading("S-" + number + " has to say which hand it is on");
        }
    }

    /** A stringer on the centreline, which only the crown stringer is. */
    public Stringer(int number) {
        this(number, Side.CENTRE);
    }

    /** Read the written form: {@code S-14L}, {@code S14R}, {@code S-0}. */
    public static Stringer parse(String text) {
        Matcher match = SHAPE.matcher(Text.strip(text));
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not a stringer");
        }
        int number = Integer.parseInt(match.group(1));
        String hand = match.group(2);
        if (number == CROWN) {
            return new Stringer(CROWN);
        }
        if (hand == null) {
            throw new BadReading(Literal.of(text) + " does not say which hand");
        }
        return new Stringer(number, Text.upper(hand).equals("L") ? Side.LEFT : Side.RIGHT);
    }

    /** Whether this is the crown stringer. */
    public boolean isCrown() {
        return number == CROWN;
    }

    /** The same stringer on the other hand. The crown is its own mirror. */
    public Stringer mirrored() {
        if (isCrown()) {
            return this;
        }
        return new Stringer(number, side == Side.LEFT ? Side.RIGHT : Side.LEFT);
    }

    /** Higher on the barrel, which is the lower number. */
    public boolean isAbove(Stringer other) {
        return number < other.number;
    }

    /** How many stringer pitches apart, on the same hand. */
    public int spansTo(Stringer other) {
        if (side != other.side && !(isCrown() || other.isCrown())) {
            throw new BadReading("stringers on opposite hands do not span");
        }
        return Math.abs(number - other.number);
    }

    /** By number, then by hand. */
    @Override
    public int compareTo(Stringer other) {
        return ORDER.compare(this, other);
    }

    /** {@code S-14L}, or {@code S-0} for the crown. */
    @Override
    public String toString() {
        return isCrown() ? "S-" + number : "S-" + number + side;
    }
}
