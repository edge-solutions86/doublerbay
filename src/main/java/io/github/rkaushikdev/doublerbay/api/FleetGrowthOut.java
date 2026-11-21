package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.List;

/**
 * The cracks on the fleet growing faster than the programme expects.
 *
 * @param on the day the list was asked for
 * @param tracked how many cracks the fleet carries
 * @param fast the fast ones, fastest first
 */
public record FleetGrowthOut(LocalDate on, int tracked, List<GrowthOut> fast) {

    public FleetGrowthOut {
        fast = List.copyOf(fast);
    }
}
