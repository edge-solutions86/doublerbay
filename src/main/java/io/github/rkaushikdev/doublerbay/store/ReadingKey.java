package io.github.rkaushikdev.doublerbay.store;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** The key of a counter reading: the tail and the day it was taken. */
public final class ReadingKey implements Serializable {

    private String serial;
    private LocalDate taken;

    /** An empty key, for the persistence provider. */
    public ReadingKey() {}

    /** The reading on one tail on one day. */
    public ReadingKey(String serial, LocalDate taken) {
        this.serial = serial;
        this.taken = taken;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReadingKey key
                && Objects.equals(key.serial, serial)
                && Objects.equals(key.taken, taken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serial, taken);
    }
}
