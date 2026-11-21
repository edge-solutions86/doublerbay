package io.github.rkaushikdev.doublerbay.repair;

import java.util.Objects;

/**
 * Two fitted repairs inside the clearance one of them needs, the earlier in the list first.
 *
 * <p>{@link RepairRecord#clashes} finds them.
 */
public record Clash(RepairRecord one, RepairRecord other) {

    /** A pair of repairs. */
    public Clash {
        Objects.requireNonNull(one, "one");
        Objects.requireNonNull(other, "other");
    }

    /** The two repair numbers, the way a report lists the pair. */
    public RepairPair numbers() {
        return new RepairPair(one.reference(), other.reference());
    }
}
