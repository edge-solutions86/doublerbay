package io.github.rkaushikdev.doublerbay.assess;

import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Joint;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** One damage that landed on a joint, with the joint it landed on. */
public record JointCase(Case subject, Joint joint) {

    /** A damage and the joint under it. */
    public JointCase {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(joint, "joint");
    }

    /** How far the damage is off the member the joint follows. */
    public Length margin() {
        return subject.placement().edgeMargin();
    }

    /** How far the damage is from the row nearest it. */
    public Length toTheRow() {
        return joint.distanceToNearestRow(margin());
    }

    /** How far off the row, in fastener diameters, if a size is known. */
    public Optional<BigDecimal> inDiameters() {
        Optional<FastenerSize> size = subject.placement().nearestFastener();
        if (size.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Decimals.over(
                BigDecimal.valueOf(toTheRow().mils()),
                BigDecimal.valueOf(size.get().diameter().mils())));
    }

    /** Whether the damage is near enough a row to count as in it, which needs a known fastener. */
    public boolean isInTheRow() {
        Optional<BigDecimal> held = inDiameters();
        return held.isPresent() && held.get().compareTo(Joints.IN_THE_ROW) <= 0;
    }

    /** {@code DR-2025-000001 on lap joint on S-6L, 1 rows}. */
    @Override
    public String toString() {
        return subject.finding().reference() + " on " + joint;
    }
}
