package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.List;

/**
 * The structure audit across the register, worst first.
 *
 * @param on the day it was asked for
 * @param tails one line per tail in service on the day
 * @param flagged how many of those are not clean
 */
public record FleetAuditOut(LocalDate on, List<FleetLineOut> tails, int flagged) {

    public FleetAuditOut {
        tails = List.copyOf(tails);
    }
}
