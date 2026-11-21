package io.github.rkaushikdev.doublerbay.assess;

import java.util.Objects;

/**
 * One rule's answer, with what the disposition becomes if it failed.
 *
 * <p>Failing a rule does not land in the same place for every rule. Failing a depth cap on a scratch
 * means grind it out, failing it on a dent means repair it, and the rule says which when it answers.
 */
public record Check(Reason reason, Disposition onFailure) {

    /** A rule's reason and where a failure lands. */
    public Check {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(onFailure, "onFailure");
    }

    /** Whether the damage got past the rule. */
    public boolean passed() {
        return reason.passed();
    }

    /** What the rule says the disposition is: allowable when it passed, its failure otherwise. */
    public Disposition outcome() {
        return passed() ? Disposition.ALLOWABLE : onFailure;
    }

    /** The reason, {@code ok  depth: 0.020 deep against a cap of 0.060 at 0.040}. */
    @Override
    public String toString() {
        return reason.toString();
    }
}
