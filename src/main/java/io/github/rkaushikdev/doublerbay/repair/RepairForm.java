package io.github.rkaushikdev.doublerbay.repair;

import io.github.rkaushikdev.doublerbay.util.Literal;

/**
 * How a patch sits relative to the skin.
 *
 * <p>An external patch is quicker and it stands proud of the skin, which the aerodynamicists will not
 * have forward of the wing on the upper surfaces, so those go flush and take twice as long.
 */
public enum RepairForm {
    EXTERNAL("external doubler"),
    FLUSH("flush doubler"),
    INTERNAL("internal doubler");

    private final String value;

    RepairForm(String value) {
        this.value = value;
    }

    /** The words the drawing uses: {@code external doubler}. */
    public String value() {
        return value;
    }

    /**
     * The form these words name.
     *
     * @throws IllegalArgumentException when no form is
     */
    public static RepairForm fromValue(String value) {
        for (RepairForm form : values()) {
            if (form.value.equals(value)) {
                return form;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid RepairForm");
    }

    /** Whether the patch stands proud of the skin, in the airflow. */
    public boolean isInTheAirflow() {
        return this == EXTERNAL;
    }

    /** {@code external doubler}. */
    @Override
    public String toString() {
        return value;
    }
}
