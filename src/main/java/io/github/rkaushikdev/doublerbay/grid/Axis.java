package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.WrongMeasure;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import io.github.rkaushikdev.doublerbay.util.Literal;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One reading on one of the rulers the airframe is dimensioned against. The rulers differ only in
 * what they are called.
 *
 * <p>Station runs aft from a datum somewhere ahead of the nose, waterline runs up from a datum
 * somewhere below the keel, and buttock line runs outboard from the centreline in both directions.
 * All three are inches, all three are written on the same drawing, and none of them means anything
 * without its letter.
 *
 * <p>They are separate types on purpose. A station of 727.5 and a waterline of 727.5 are not the same
 * place and comparing them is always a mistake, so the comparison refuses instead of quietly
 * answering. That has caught more transcription errors off a damage report than any validation rule
 * in this package.
 */
public abstract sealed class Axis implements Comparable<Axis> permits Station, Waterline, ButtockLine, WingStation {

    private final String mark;
    private final Length at;

    /** A reading on the ruler this mark names. */
    protected Axis(String mark, Length at) {
        this.mark = Objects.requireNonNull(mark, "mark");
        this.at = Objects.requireNonNull(at, "at");
    }

    /**
     * A reading taken off a callout, refused as not being one when the decimal does not read.
     *
     * @param what the ruler's name, for the refusal
     */
    static Length inches(String text, String what) {
        BigDecimal value;
        try {
            value = Decimals.parse(text);
        } catch (NumberFormatException unreadable) {
            throw new BadReading(Literal.of(text) + " is not a " + what);
        }
        return Length.ofInches(value);
    }

    /** The letters the ruler is written with: {@code STA}, {@code WL}, {@code BL}, {@code WS}. */
    public String mark() {
        return mark;
    }

    /** The reading as a length from the ruler's datum. */
    public Length at() {
        return at;
    }

    /** The reading in inches, to three places. */
    public BigDecimal inches() {
        return at.inches();
    }

    /** The same ruler, moved along by a distance. */
    public abstract Axis offset(Length distance);

    /** How far apart two readings on the same ruler are. */
    public Length gapTo(Axis other) {
        sameRuler(other);
        return other.at.minus(at).abs();
    }

    private void sameRuler(Axis other) {
        if (getClass() != other.getClass()) {
            throw new WrongMeasure(mark + " and " + other.mark + " are different rulers");
        }
    }

    /** Readings on one ruler order by distance from its datum; two rulers never order. */
    @Override
    public int compareTo(Axis other) {
        sameRuler(other);
        return at.compareTo(other.at);
    }

    /** Whether this reading is before another on the same ruler. */
    public boolean isLessThan(Axis other) {
        return compareTo(other) < 0;
    }

    /** Whether this reading is no further along than another on the same ruler. */
    public boolean isAtMost(Axis other) {
        return compareTo(other) <= 0;
    }

    /** Whether this reading is past another on the same ruler. */
    public boolean isGreaterThan(Axis other) {
        return compareTo(other) > 0;
    }

    /** Whether this reading is at least as far along as another on the same ruler. */
    public boolean isAtLeast(Axis other) {
        return compareTo(other) >= 0;
    }

    /** Two readings are equal only on the same ruler at the same distance. */
    @Override
    public boolean equals(Object other) {
        return other instanceof Axis axis && axis.getClass() == getClass() && axis.at.equals(at);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mark, at);
    }

    /** The expression that builds this reading again: {@code Station.ofInches("360.000")}. */
    public String repr() {
        return getClass().getSimpleName() + ".ofInches(\"" + inches().toPlainString() + "\")";
    }

    /** {@code STA 727.500}. */
    @Override
    public String toString() {
        return mark + " " + inches().toPlainString();
    }
}
