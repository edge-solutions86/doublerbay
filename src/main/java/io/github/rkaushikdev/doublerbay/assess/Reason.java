package io.github.rkaushikdev.doublerbay.assess;

import java.util.Objects;

/** One rule, what it was checking, and whether the damage got past it. */
public record Reason(String rule, String detail, boolean passed) {

    /** A rule with what it read. */
    public Reason {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(detail, "detail");
    }

    /** {@code ok  depth: 0.030 against a cap of 0.060}, or {@code no  ...} when it failed. */
    @Override
    public String toString() {
        String mark = passed ? "ok" : "no";
        return mark + "  " + rule + ": " + detail;
    }
}
