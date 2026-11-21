package io.github.rkaushikdev.doublerbay.fleet;

import java.math.BigDecimal;
import java.util.Objects;

/** How hard a tail is flying: cycles a day and hours a day, each to three places. */
public record DailyRate(BigDecimal cyclesADay, BigDecimal hoursADay) {

    /** A rate on both counters. */
    public DailyRate {
        Objects.requireNonNull(cyclesADay, "cyclesADay");
        Objects.requireNonNull(hoursADay, "hoursADay");
    }
}
