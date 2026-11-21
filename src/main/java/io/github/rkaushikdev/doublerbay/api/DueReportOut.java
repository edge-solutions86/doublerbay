package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.List;

/**
 * What falls due on one tail, as it goes back.
 *
 * @param serial the tail
 * @param on the day it was asked for
 * @param measuredRate whether the days come off the tail's own rate rather than the planning rate
 * @param grounded whether anything on it is already past its limit
 * @param items every limit, worst first
 */
public record DueReportOut(
        String serial, LocalDate on, boolean measuredRate, boolean grounded, List<DueItemOut> items) {

    public DueReportOut {
        items = List.copyOf(items);
    }
}
