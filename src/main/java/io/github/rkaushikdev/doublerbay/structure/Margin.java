package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.grid.JointMember;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.Objects;

/** How far a point is off the nearest barrel member, and which member that is. */
public record Margin(JointMember member, Length distance) {

    /** A distance off a member. */
    public Margin {
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(distance, "distance");
    }

    /** {@code 3.000 off S-2L}. */
    @Override
    public String toString() {
        return distance.inches().toPlainString() + " off " + member;
    }
}
