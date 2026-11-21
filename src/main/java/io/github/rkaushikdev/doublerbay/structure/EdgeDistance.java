package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.Objects;

/** One margin measurement, to one named edge: {@code 1.500 to W12L}. */
public record EdgeDistance(EdgeKind kind, String to, Length distance) {

    /** A distance to a named edge of a kind. */
    public EdgeDistance {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(distance, "distance");
    }

    /** {@code 1.500 to W12L}. */
    @Override
    public String toString() {
        return distance.inches().toPlainString() + " to " + to;
    }
}
