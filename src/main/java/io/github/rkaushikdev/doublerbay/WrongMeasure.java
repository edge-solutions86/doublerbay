package io.github.rkaushikdev.doublerbay;

/** Two quantities that are not the same kind of thing were mixed. */
public class WrongMeasure extends DoublerbayError {

    /** A mix-up refused with what was mixed. */
    public WrongMeasure(String message) {
        super(message);
    }
}
