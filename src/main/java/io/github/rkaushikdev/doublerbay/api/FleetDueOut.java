package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.List;

/**
 * What falls due across the register, worst tail first.
 *
 * @param on the day it was asked for
 * @param tails one due report per tail
 * @param overrun how many limits across all of them have already passed
 */
public record FleetDueOut(LocalDate on, List<DueReportOut> tails, int overrun) {

    public FleetDueOut {
        tails = List.copyOf(tails);
    }
}
