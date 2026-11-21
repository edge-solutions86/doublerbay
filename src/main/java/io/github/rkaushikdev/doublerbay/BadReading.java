package io.github.rkaushikdev.doublerbay;

/** A value on the card cannot be read, or is not one this aircraft can carry. */
public class BadReading extends DoublerbayError {

    /** A reading refused with the sentence the desk would say out loud. */
    public BadReading(String message) {
        super(message);
    }
}
