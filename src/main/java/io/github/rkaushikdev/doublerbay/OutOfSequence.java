package io.github.rkaushikdev.doublerbay;

/** A record was asked to do something its current state does not allow. */
public class OutOfSequence extends DoublerbayError {

    /** A move refused, naming the state that refused it. */
    public OutOfSequence(String message) {
        super(message);
    }
}
