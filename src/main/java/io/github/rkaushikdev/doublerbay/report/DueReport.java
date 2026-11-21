package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.ids.Serial;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * What falls due on one tail as of a day, worst first, and whether the rate behind it is the
 * aeroplane's own.
 *
 * <p>A margin computed off the planning rate is a different quality of answer from one computed off
 * the aeroplane's own flying, so the line says when that happened.
 */
public record DueReport(Serial serial, LocalDate on, List<DueItem> items, boolean measuredRate) {

    /** One tail's due list as of a day. */
    public DueReport {
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(on, "on");
        items = List.copyOf(items);
    }

    /** The lines already past their limit. */
    public List<DueItem> overrun() {
        return DueList.overrun(items);
    }

    /** Whether anything on the tail is past its limit, which grounds it. */
    public boolean isGrounded() {
        return !overrun().isEmpty();
    }

    /** {@code MSN 5127: 3 due, 1 over}, with {@code , on the planning rate} when it was. */
    @Override
    public String toString() {
        String note = measuredRate ? "" : ", on the planning rate";
        return serial + ": " + items.size() + " due, " + overrun().size() + " over" + note;
    }
}
