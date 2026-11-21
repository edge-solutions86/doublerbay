package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.DueAt;
import java.util.Objects;

/** One thing falling due on one tail, with the margin worked out. */
public record DueItem(Serial serial, FindingId against, String what, DueAt due, Margin margin) {

    /** A line on the due list. */
    public DueItem {
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(against, "against");
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(due, "due");
        Objects.requireNonNull(margin, "margin");
    }

    /** Whether the limit has already been passed. */
    public boolean isOverrun() {
        return margin.isOverrun();
    }

    /** {@code MSN 5127 DR-2025-000041 deferral, re inspect detailed visual: 125 days on flight cycles}. */
    @Override
    public String toString() {
        return serial + " " + against + " " + what + ": " + margin;
    }
}
