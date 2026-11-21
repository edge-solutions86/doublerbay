package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A three digit zone, as painted on the panel, which is how a finding gets written down before
 * anyone measures it.
 *
 * <p>The mechanic who finds the damage does not know the panel number. What they write on the card
 * is the zone, because the zone is painted on the access panel they opened and printed on every page
 * of the maintenance manual. Three digits: the hundred says which end of the aircraft, the ten says
 * which part of it, and the unit says which side and which bay.
 *
 * <p>The side is carried in the last digit and it is a convention, not arithmetic. On the V320 an
 * odd unit digit is the left hand side and an even one is the right, which is the same way the type
 * certificate holder numbers its access panels. Zero is centreline and belongs to neither.
 */
public record Zone(int number) implements Comparable<Zone> {

    private static final Pattern SHAPE = Pattern.compile("([0-9]{3})");

    /** A zone from 100 to 899. */
    public Zone {
        if (number < 100 || number > 899) {
            throw new BadReading("a zone is three digits from 100 to 899, got " + number);
        }
    }

    /** Read the painted number. */
    public static Zone parse(String text) {
        Matcher match = SHAPE.matcher(Text.strip(text));
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not a zone number");
        }
        return new Zone(Integer.parseInt(match.group(1)));
    }

    /** The hand a unit digit stands for: zero is centreline, odd is left, even is right. */
    public static Side sideOfDigit(int unit) {
        if (unit == 0) {
            return Side.CENTRE;
        }
        return Math.floorMod(unit, 2) != 0 ? Side.LEFT : Side.RIGHT;
    }

    /** Build a run of zones inside one major zone, checking they belong to it. */
    public static List<Zone> zonesIn(MajorZone major, List<Integer> numbers) {
        Objects.requireNonNull(major, "major");
        List<Zone> built = new ArrayList<>();
        for (int number : numbers) {
            Zone zone = new Zone(number);
            if (zone.major() != major) {
                throw new BadReading("zone " + zone + " is not in " + major.title());
            }
            built.add(zone);
        }
        return List.copyOf(built);
    }

    /** The hundred digit, which end of the aircraft. */
    public MajorZone major() {
        return MajorZone.fromValue(number / 100 * 100);
    }

    /** The ten digit, which is the part of the major zone. */
    public int sub() {
        return number / 10 % 10;
    }

    /** The unit digit, which carries the hand. */
    public int unit() {
        return number % 10;
    }

    /** The hand the unit digit says. */
    public Side side() {
        return sideOfDigit(unit());
    }

    /** Either half of the barrel. */
    public boolean isFuselage() {
        MajorZone major = major();
        return major == MajorZone.LOWER_FUSELAGE || major == MajorZone.UPPER_FUSELAGE;
    }

    /** Either wing. */
    public boolean isWing() {
        MajorZone major = major();
        return major == MajorZone.LEFT_WING || major == MajorZone.RIGHT_WING;
    }

    /**
     * The mirror of this zone, for damage reported on the wrong hand.
     *
     * <p>Centreline zones have no mirror and come back unchanged, which is the right answer and not
     * a refusal.
     */
    public Zone opposite() {
        if (side() == Side.CENTRE) {
            return this;
        }
        int step = side() == Side.RIGHT ? -1 : 1;
        return new Zone(number + step);
    }

    /** Same place on the aircraft, either hand. */
    public boolean sharesBayWith(Zone other) {
        return number / 10 == other.number / 10;
    }

    @Override
    public int compareTo(Zone other) {
        return Integer.compare(number, other.number);
    }

    /** Whether this zone numbers below another. */
    public boolean isLessThan(Zone other) {
        return number < other.number;
    }

    /** {@code 231}. */
    @Override
    public String toString() {
        return String.valueOf(number);
    }
}
