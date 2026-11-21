package io.github.rkaushikdev.doublerbay.measure;

/**
 * What a directional damage is lying along, once its axis is folded.
 *
 * <p>The same half inch of crack is a hold lying along the body axis and a monitor lying around
 * the barrel, so this is most of what the desk wants out of an {@link Angle}.
 */
public enum Orientation {
    LONGITUDINAL("longitudinal"),
    CIRCUMFERENTIAL("circumferential"),
    OBLIQUE("oblique");

    private final String value;

    Orientation(String value) {
        this.value = value;
    }

    /** The word the report form prints. */
    public String value() {
        return value;
    }

    /** {@code Orientation.LONGITUDINAL}, the type and the member. */
    @Override
    public String toString() {
        return "Orientation." + name();
    }
}
