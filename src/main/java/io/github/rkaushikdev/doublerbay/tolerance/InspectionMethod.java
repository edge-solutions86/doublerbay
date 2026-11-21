package io.github.rkaushikdev.doublerbay.tolerance;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Literal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The ways the shop can be asked to look at something, and what each way of doing it can find.
 *
 * <p>Setting a repeat inspection is not much use without saying how. A man walking round the
 * aeroplane will see a two inch crack and will not see a quarter inch one, and a quarter inch crack
 * under a doubler he will not see at all because it is on the other side of the skin. So the method
 * is chosen from what has to be found rather than from what is convenient, and the cheapest method
 * that can find it is the one that goes on the card.
 *
 * <p>The order they are listed in is the order of cost, not of sensitivity. Eddy current finds far
 * smaller cracks than a detailed visual and it needs the paint off, a trained operator and an
 * aeroplane that is not going anywhere, so it is the last thing chosen and not the first.
 */
public enum InspectionMethod {
    GVI("general visual", "2.000", 0),
    DVI("detailed visual", "0.500", 1),
    XRAY("radiographic", "0.250", 2),
    UT("ultrasonic", "0.100", 3),
    LFEC("low frequency eddy current", "0.100", 4),
    HFEC("high frequency eddy current", "0.050", 5);

    private final String value;
    private final Length findsDownTo;
    private final int costRank;

    InspectionMethod(String value, String findsDownTo, int costRank) {
        this.value = value;
        this.findsDownTo = Length.ofInches(findsDownTo);
        this.costRank = costRank;
    }

    /** The name the card uses. */
    public String value() {
        return value;
    }

    /**
     * The method the card names with these words.
     *
     * @throws IllegalArgumentException when no method is
     */
    public static InspectionMethod fromValue(String value) {
        for (InspectionMethod method : values()) {
            if (method.value.equals(value)) {
                return method;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid InspectionMethod");
    }

    /** The smallest crack this method can be relied on to find. */
    public Length findsDownTo() {
        return findsDownTo;
    }

    /** How expensive it is to run, cheapest first. */
    public int costRank() {
        return costRank;
    }

    /** Anything that needs an instrument and an operator rather than a light. */
    public boolean isNdt() {
        return this != GVI && this != DVI;
    }

    /** Whether the paint has to come off first. */
    public boolean needsPaintOff() {
        return this == HFEC || this == LFEC;
    }

    /** Whether it finds damage on the side of the skin nobody can see. */
    public boolean reachesTheFarSide() {
        return this == LFEC || this == UT || this == XRAY;
    }

    /** Whether it can be relied on to find something this size. */
    public boolean canFind(Length size) {
        if (size.mils() <= 0) {
            throw new BadReading("nothing has no size to find");
        }
        return size.isAtLeast(findsDownTo);
    }

    /** Every method, cheapest first. */
    public static List<InspectionMethod> byCost() {
        return Arrays.stream(values())
                .sorted(Comparator.comparingInt(InspectionMethod::costRank))
                .toList();
    }

    /** The cheapest method that can be relied on to find something of this size. */
    public static InspectionMethod cheapestThatFinds(Length size) {
        return cheapestThatFinds(size, false);
    }

    /** The cheapest method that can be relied on to find something of this size, on the far side if asked. */
    public static InspectionMethod cheapestThatFinds(Length size, boolean farSide) {
        for (InspectionMethod method : byCost()) {
            if (farSide && !method.reachesTheFarSide()) {
                continue;
            }
            if (method.canFind(size)) {
                return method;
            }
        }
        throw new NotOnFile("nothing on file finds " + size.inches().toPlainString() + " reliably");
    }

    /** Whether asking for this method would actually find what is being looked for. */
    public static boolean isCredible(InspectionMethod method, Length size) {
        return isCredible(method, size, false);
    }

    /** Whether asking for this method would actually find what is being looked for, on the far side if asked. */
    public static boolean isCredible(InspectionMethod method, Length size, boolean farSide) {
        if (farSide && !method.reachesTheFarSide()) {
            return false;
        }
        return method.canFind(size);
    }

    @Override
    public String toString() {
        return value;
    }
}
