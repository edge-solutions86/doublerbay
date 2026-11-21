package io.github.rkaushikdev.doublerbay.store;

import java.io.Serializable;
import java.util.Objects;

/** The key of a damage card row: the card number and its revision. */
public final class FindingKey implements Serializable {

    private String reference;
    private int revision;

    /** An empty key, for the persistence provider. */
    public FindingKey() {}

    /** One revision of one card. */
    public FindingKey(String reference, int revision) {
        this.reference = reference;
        this.revision = revision;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FindingKey key && Objects.equals(key.reference, reference) && key.revision == revision;
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference, revision);
    }
}
