package io.github.rkaushikdev.doublerbay.service;

import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.repair.RepairPair;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionSchedule;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What fitting one repair changed.
 *
 * @param reference the repair that went on
 * @param state where it left the card it answers
 * @param deferralLifted whether a deferral was lifted because of it
 * @param clashes the pairs on the panel now too close together, this repair included
 * @param inspection the repeat inspection it carries, if its category carries one
 */
public record Embodiment(
        RepairId reference,
        DispositionState state,
        boolean deferralLifted,
        List<RepairPair> clashes,
        Optional<InspectionSchedule> inspection) {

    public Embodiment {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(state, "state");
        clashes = List.copyOf(clashes);
        Objects.requireNonNull(inspection, "inspection");
    }

    /** A fit with no clashes and no repeat inspection. */
    public Embodiment(RepairId reference, DispositionState state, boolean deferralLifted) {
        this(reference, state, deferralLifted, List.of(), Optional.empty());
    }

    /** A fit with its clashes and no repeat inspection. */
    public Embodiment(RepairId reference, DispositionState state, boolean deferralLifted, List<RepairPair> clashes) {
        this(reference, state, deferralLifted, clashes, Optional.empty());
    }

    /** Whether the repair went on clear of every other repair on the panel. */
    public boolean isClean() {
        return clashes.isEmpty();
    }

    /** Whether the repair carries a repeat inspection. */
    public boolean carriesInspection() {
        return inspection.isPresent();
    }

    /** {@code RP-53-0001 fitted, card closed}, with the clashes counted after it when there are any. */
    @Override
    public String toString() {
        String tail = clashes.isEmpty() ? "" : ", " + clashes.size() + " clashes";
        return reference + " fitted, card " + state + tail;
    }
}
