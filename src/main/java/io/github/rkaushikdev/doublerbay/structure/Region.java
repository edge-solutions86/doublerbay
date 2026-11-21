package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.util.Literal;

/**
 * Which of a panel's features a spot belongs to, tightest first.
 *
 * <p>The tightest rule wins, and the order they win in is fixed here: what is under an existing
 * repair, then what is at a cutout corner, then the rest of the cutout band, then a fastener row,
 * then the joint band around it, then the chem mill step, then the land, and only what is left over
 * is open field.
 */
public enum Region {
    UNDER_DOUBLER("under an installed doubler"),
    CUTOUT_CORNER("at a cutout corner"),
    CUTOUT_EDGE("in a cutout keep out"),
    FASTENER_ROW("on a fastener row"),
    JOINT_BAND("in a joint band"),
    CHEM_MILL_STEP("on a chem mill step"),
    LAND("on a land"),
    OPEN_FIELD("in open field");

    private final String value;

    Region(String value) {
        this.value = value;
    }

    /** The region these words name. */
    public static Region fromValue(String value) {
        for (Region region : values()) {
            if (region.value.equals(value)) {
                return region;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid Region");
    }

    /** The words, {@code on a fastener row}. */
    public String value() {
        return value;
    }

    /** Where the region stands in the order, nought for the tightest. */
    public int rank() {
        return ordinal();
    }

    /** Whether the spot is in open field, which nothing else reached. */
    public boolean isOpenField() {
        return this == OPEN_FIELD;
    }

    /** Whether this region's rules are tighter than another's. */
    public boolean tighterThan(Region other) {
        return rank() < other.rank();
    }

    /** {@code in open field}. */
    @Override
    public String toString() {
        return value;
    }
}
