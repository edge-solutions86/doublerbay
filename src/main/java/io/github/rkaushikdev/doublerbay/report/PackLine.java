package io.github.rkaushikdev.doublerbay.report;

import java.util.Objects;

/**
 * One thing to do at a check, whatever register it came off.
 *
 * @param what the block it belongs to: deferral, inspection or waiting
 * @param reference the card or repair it is against, as printed
 * @param detail what is owed, in words
 * @param overrun whether it was already over when the check opened
 */
public record PackLine(String what, String reference, String detail, boolean overrun) {

    public PackLine {
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(detail, "detail");
    }

    /** A line that is not over. */
    public PackLine(String what, String reference, String detail) {
        this(what, reference, detail, false);
    }

    /** {@code deferral DR-2025-000001: detailed visual, 3 days on calendar}, marked {@code OVER} at the front when over. */
    @Override
    public String toString() {
        String mark = overrun ? "OVER " : "";
        return mark + what + " " + reference + ": " + detail;
    }
}
