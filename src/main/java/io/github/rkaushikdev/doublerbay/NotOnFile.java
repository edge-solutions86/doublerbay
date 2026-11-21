package io.github.rkaushikdev.doublerbay;

/** Something was looked up and the reference data does not hold it. */
public class NotOnFile extends DoublerbayError {

    /** A lookup miss, naming what was looked for. */
    public NotOnFile(String message) {
        super(message);
    }
}
