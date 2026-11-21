package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** A panel that has been graded more than once, how often, and over what span. */
public record RepeatLocation(PanelId part, int times, LocalDate first, LocalDate latest) {

    private static final BigDecimal DAYS_A_YEAR = BigDecimal.valueOf(365);

    /** A panel, how often it was graded, and the first and latest gradings. */
    public RepeatLocation {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(latest, "latest");
    }

    /** Graded often enough to say something about the programme rather than the panel. */
    public boolean isPersistent() {
        return times >= ProgrammeReport.REPEAT_THRESHOLD;
    }

    /** The span from the first grading to the latest, in years of 365 days, to three places, a half rounding up. */
    public BigDecimal yearsBetween() {
        BigDecimal days = BigDecimal.valueOf(ChronoUnit.DAYS.between(first, latest));
        return Decimals.over(days, DAYS_A_YEAR).setScale(ProgrammeReport.PLACES, RoundingMode.HALF_UP);
    }

    /** {@code 5310-143L graded 3 times over 2.000 years}. */
    @Override
    public String toString() {
        return part + " graded " + times + " times over " + Decimals.text(yearsBetween()) + " years";
    }
}
