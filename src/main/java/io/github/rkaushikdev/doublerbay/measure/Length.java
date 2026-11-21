package io.github.rkaushikdev.doublerbay.measure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A distance along or through the skin, to the nearest thousandth of an inch.
 *
 * <p>Everything on the shop floor is called out in decimal inches to three places. Skin thickness
 * is 0.040, edge margin is 0.320, a body station is 727.5. Binary fractions lose the third place
 * often enough to matter when a limit is exactly 0.040 and the damage is exactly 0.040, so a length
 * is an integer count of mils and the decimal conversion happens at the edges.
 *
 * <p>Fastener and drill callouts come in as fractions instead, so {@link #parse} takes both forms
 * and a mixed number besides.
 */
public final class Length implements Comparable<Length> {

    /** Thousandths in an inch. */
    public static final int MILS_PER_INCH = 1000;

    /** Millimetres in an inch, exactly. */
    public static final BigDecimal MM_PER_INCH = new BigDecimal("25.4");

    /** No distance at all. */
    public static final Length ZERO = new Length(0);

    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)");
    private static final Pattern FRACTION = Pattern.compile("([+-]?)(?:([0-9]+)[ \\-])?([0-9]+)/([0-9]+)");

    private final int mils;

    /** A length of a whole number of thousandths of an inch. */
    public Length(int mils) {
        this.mils = mils;
    }

    /** A length written in decimal inches, refused if it is finer than a thousandth. */
    public static Length ofInches(BigDecimal value) {
        BigDecimal scaled = Decimals.times(value, MILS_PER_INCH);
        if (!Decimals.isWhole(scaled)) {
            throw new BadReading(Decimals.text(value) + " in is finer than a thousandth");
        }
        return new Length(Decimals.toInt(scaled));
    }

    /** A length written in decimal inches, as text. */
    public static Length ofInches(String value) {
        return ofInches(Decimals.parse(value));
    }

    /** A whole number of inches. */
    public static Length ofInches(long value) {
        return ofInches(BigDecimal.valueOf(value));
    }

    /** A length in millimetres, rounded to the nearest thousandth of an inch. */
    public static Length ofMm(BigDecimal value) {
        BigDecimal inches = Decimals.over(value, MM_PER_INCH);
        return new Length(Decimals.toInt(Decimals.times(inches, MILS_PER_INCH)));
    }

    /** A length in millimetres, as text. */
    public static Length ofMm(String value) {
        return ofMm(Decimals.parse(value));
    }

    /**
     * Read a callout in either of the two forms the shop writes.
     *
     * <p>{@code 0.040} and {@code 727.5} are decimal inches. {@code 3/32}, {@code 1-1/4} and
     * {@code 1 1/4} are the fractional callouts that come off a drill chart, rounded to the nearest
     * thousandth.
     */
    public static Length parse(String text) {
        String cleaned = Text.strip(text);
        if (cleaned.isEmpty()) {
            throw new BadReading("empty length");
        }
        if (DECIMAL.matcher(cleaned).matches()) {
            return ofInches(new BigDecimal(cleaned));
        }
        Matcher match = FRACTION.matcher(cleaned);
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not a length callout");
        }
        BigDecimal denominator = new BigDecimal(match.group(4));
        if (denominator.signum() == 0) {
            throw new BadReading(Literal.of(text) + " divides by zero");
        }
        BigDecimal whole = match.group(2) == null ? BigDecimal.ZERO : new BigDecimal(match.group(2));
        BigDecimal total = whole.add(Decimals.over(new BigDecimal(match.group(3)), denominator), Decimals.CONTEXT);
        if (match.group(1).equals("-")) {
            total = total.negate();
        }
        return new Length(Decimals.toInt(Decimals.times(total, MILS_PER_INCH)));
    }

    /** The length as a count of thousandths of an inch. */
    public int mils() {
        return mils;
    }

    /** The length in inches, to three places. */
    public BigDecimal inches() {
        return BigDecimal.valueOf(mils, 3);
    }

    /** The length in millimetres, to two places. */
    public BigDecimal mm() {
        return Decimals.quantize(inches().multiply(MM_PER_INCH), 2);
    }

    /** The two lengths end to end. */
    public Length plus(Length other) {
        return new Length(mils + other.mils);
    }

    /** This length with another taken off it, which can go below nothing. */
    public Length minus(Length other) {
        return new Length(mils - other.mils);
    }

    /** This length a whole number of times over. */
    public Length times(long factor) {
        return times(BigDecimal.valueOf(factor));
    }

    /** This length scaled, rounded to the nearest thousandth with a tie going to the even one. */
    public Length times(BigDecimal factor) {
        return new Length(Decimals.toInt(Decimals.times(BigDecimal.valueOf(mils), factor)));
    }

    /** The same distance the other way. */
    public Length negate() {
        return new Length(-mils);
    }

    /** The distance without its direction. */
    public Length abs() {
        return new Length(Math.abs(mils));
    }

    /** Whether there is no distance at all. */
    public boolean isZero() {
        return mils == 0;
    }

    /** Whether this is shorter than another length. */
    public boolean isLessThan(Length other) {
        return mils < other.mils;
    }

    /** Whether this is no longer than another length. */
    public boolean isAtMost(Length other) {
        return mils <= other.mils;
    }

    /** Whether this is longer than another length. */
    public boolean isGreaterThan(Length other) {
        return mils > other.mils;
    }

    /** Whether this is at least as long as another length. */
    public boolean isAtLeast(Length other) {
        return mils >= other.mils;
    }

    /** The shorter of two lengths, the first when they are equal. */
    public static Length min(Length first, Length second) {
        return second.mils < first.mils ? second : first;
    }

    /** The longer of two lengths, the first when they are equal. */
    public static Length max(Length first, Length second) {
        return second.mils > first.mils ? second : first;
    }

    @Override
    public int compareTo(Length other) {
        return Integer.compare(mils, other.mils);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Length length && length.mils == mils;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(mils);
    }

    /** The expression that builds this length again: {@code Length.ofInches("0.040")}. */
    public String repr() {
        return "Length.ofInches(\"" + inches().toPlainString() + "\")";
    }

    /** {@code 0.040 in}. */
    @Override
    public String toString() {
        return inches().toPlainString() + " in";
    }
}
