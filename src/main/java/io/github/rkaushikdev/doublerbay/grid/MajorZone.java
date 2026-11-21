package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;

/** The hundred digit of a zone, which is the coarsest thing anyone says out loud. */
public enum MajorZone {
    LOWER_FUSELAGE(100),
    UPPER_FUSELAGE(200),
    EMPENNAGE(300),
    POWERPLANT(400),
    LEFT_WING(500),
    RIGHT_WING(600),
    LANDING_GEAR(700),
    DOORS(800);

    private final int value;

    MajorZone(int value) {
        this.value = value;
    }

    /** The major zone whose hundred this is. */
    public static MajorZone fromValue(int value) {
        for (MajorZone major : values()) {
            if (major.value == value) {
                return major;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid MajorZone");
    }

    /** The hundred, {@code 200}. */
    public int value() {
        return value;
    }

    /** The major zone's name in words: {@code lower fuselage}. */
    public String title() {
        return Text.lower(name().replace('_', ' '));
    }

    /** {@code 300}. */
    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
