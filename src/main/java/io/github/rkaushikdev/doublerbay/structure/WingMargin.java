package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.grid.WingMember;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.Objects;

/** The nearest rib or spar bounding a wing panel, and how far off it a point is. */
public record WingMargin(WingMember member, Length distance) {

    /** A distance off a rib or a spar. */
    public WingMargin {
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(distance, "distance");
    }

    /** {@code 2.000 off front spar}. */
    @Override
    public String toString() {
        return distance.inches().toPlainString() + " off " + member;
    }
}
