package io.github.rkaushikdev.doublerbay.tolerance;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.Corrosion;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** One graded corrosion finding, with what the grading commits the operator to. */
public record CorrosionEntry(PanelId part, CorrosionLevel level, LocalDate foundOn) {

    /** How long the operator has to report a level three finding, in days. */
    public static final int REPORT_WITHIN_DAYS = 3;

    /** A panel, the level it was graded at, and the day it was found. */
    public CorrosionEntry {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(foundOn, "foundOn");
    }

    /**
     * Grade a finding and hand back the programme entry it produces.
     *
     * @throws BadReading for a surface stain called outside the allowable limits with no urgent concern
     *     raised, which is a contradiction: there is nothing under a stain to be outside a limit
     */
    public static CorrosionEntry grade(Corrosion finding, CorrosionContext context, LocalDate foundOn) {
        boolean stainOutsideLimits =
                finding.isSurfaceOnly() && !context.withinLimits() && !context.urgentAirworthinessConcern();
        if (stainOutsideLimits) {
            throw new BadReading(context.part() + ": a surface stain cannot be outside the allowable limits");
        }
        return new CorrosionEntry(context.part(), CorrosionLevel.levelFor(finding, context), foundOn);
    }

    /** Whether the authority has to hear about it. */
    public boolean mustBeReported() {
        return level.isReportable();
    }

    /** The last day the report can go in, for an entry that has to be reported. */
    public Optional<LocalDate> reportBy() {
        if (!mustBeReported()) {
            return Optional.empty();
        }
        return Optional.of(foundOn.plusDays(REPORT_WITHIN_DAYS));
    }

    /** Whether the report is late on a day: after the last day, never for an entry with no clock. */
    public boolean isOverdue(LocalDate today) {
        Optional<LocalDate> by = reportBy();
        return by.isPresent() && today.isAfter(by.get());
    }

    /** {@code 5310-143L level 1 on 2025-05-06}. */
    @Override
    public String toString() {
        return part + " " + level + " on " + foundOn;
    }
}
