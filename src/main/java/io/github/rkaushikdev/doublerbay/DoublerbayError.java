package io.github.rkaushikdev.doublerbay;

/**
 * Anything this package refuses on purpose.
 *
 * <p>A damage card with 0.0405 written on it and a panel number that is not on this aircraft are
 * both refusals, and they need different words back to the person holding the card. So every
 * refusal is one of a small number of kinds, and a caller that wants to tell a bad reading from a
 * genuine lookup miss catches the kind it means. A caller that does not care catches this.
 */
public abstract class DoublerbayError extends RuntimeException {

    /** A refusal that says what was wrong. */
    protected DoublerbayError(String message) {
        super(message);
    }
}
