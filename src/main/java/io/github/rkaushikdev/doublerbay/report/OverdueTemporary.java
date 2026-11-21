package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A category C repair still on, with how long it has been fitted, which may have outlived what it was
 * fitted for.
 *
 * <p>Two entries are equal when they name the same repair with the same age, however many places the
 * age was written to.
 */
public record OverdueTemporary(RepairId reference, PanelId part, LocalDate embodied, BigDecimal monthsFitted) {

    /** A repair, its panel, when it went on, and its age in months. */
    public OverdueTemporary {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(embodied, "embodied");
        Objects.requireNonNull(monthsFitted, "monthsFitted");
    }

    /** Whether it has been on longer than a temporary repair is meant to last. */
    public boolean isOverdue() {
        return monthsFitted.compareTo(BigDecimal.valueOf(StructureAudit.TEMPORARY_MONTHS)) > 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OverdueTemporary that
                && reference.equals(that.reference)
                && part.equals(that.part)
                && embodied.equals(that.embodied)
                && monthsFitted.compareTo(that.monthsFitted) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference, part, embodied, monthsFitted.stripTrailingZeros());
    }

    /** {@code RP-53-0001 on 5310-143L, 34.0 months fitted}. */
    @Override
    public String toString() {
        return reference + " on " + part + ", " + Decimals.text(monthsFitted) + " months fitted";
    }
}
