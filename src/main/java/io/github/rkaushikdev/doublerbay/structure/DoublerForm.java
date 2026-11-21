package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.util.Literal;

/** How a patch sits on the skin, which decides what it does to the airflow. */
public enum DoublerForm {
    EXTERNAL("external"),
    INTERNAL("internal"),
    FLUSH("flush");

    private final String value;

    DoublerForm(String value) {
        this.value = value;
    }

    /** The form this word names. */
    public static DoublerForm fromValue(String value) {
        for (DoublerForm form : values()) {
            if (form.value.equals(value)) {
                return form;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid DoublerForm");
    }

    /** The word, {@code external}. */
    public String value() {
        return value;
    }

    /** An external patch stands proud of the skin and the aerodynamicists care. */
    public boolean isInAirflow() {
        return this == EXTERNAL;
    }

    /** Whether the patch sits flush with the skin. */
    public boolean isFlush() {
        return this == FLUSH;
    }

    /** {@code external}. */
    @Override
    public String toString() {
        return value;
    }
}
