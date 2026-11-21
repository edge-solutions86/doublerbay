package io.github.rkaushikdev.doublerbay.repair;

import io.github.rkaushikdev.doublerbay.ids.RepairId;
import java.util.Objects;

/** Two repair numbers named together, such as the two halves of a clash. */
public record RepairPair(RepairId one, RepairId other) {

    /** A pair of repair numbers. */
    public RepairPair {
        Objects.requireNonNull(one, "one");
        Objects.requireNonNull(other, "other");
    }
}
