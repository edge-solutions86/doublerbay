package io.github.rkaushikdev.doublerbay.repair;

import io.github.rkaushikdev.doublerbay.util.Literal;

/**
 * What a repair commits the operator to, which is the part that costs money.
 *
 * <p>The category is not a description of quality. It is a commitment. Category A is permanent and
 * nobody looks at it again. Category B is permanent enough but has to be inspected on a repeat
 * interval for the rest of the aeroplane's life. Category C is temporary and has to be taken off and
 * done properly by a date.
 *
 * <p>So the category decides how much work the repair generates over twenty years, and it is decided
 * by where the repair is rather than how well it is made. A doubler on the tension surface of a wing
 * is category B however good it is, because the surface it is on grows cracks. The same doubler on a
 * tail cone is category A.
 */
public enum RepairCategory {
    A("permanent"),
    B("permanent with a repeat inspection"),
    C("temporary, to be replaced");

    private final String value;

    RepairCategory(String value) {
        this.value = value;
    }

    /** What the category commits to, in words: {@code permanent with a repeat inspection}. */
    public String value() {
        return value;
    }

    /**
     * The category these words name.
     *
     * @throws IllegalArgumentException when no category is
     */
    public static RepairCategory fromValue(String value) {
        for (RepairCategory category : values()) {
            if (category.value.equals(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid RepairCategory");
    }

    /** Whether the repair has to be looked at again. */
    public boolean needsInspection() {
        return this != A;
    }

    /** Whether the repair may stay on for good. */
    public boolean isPermanent() {
        return this != C;
    }

    /** {@code category B}. */
    @Override
    public String toString() {
        return "category " + name();
    }
}
