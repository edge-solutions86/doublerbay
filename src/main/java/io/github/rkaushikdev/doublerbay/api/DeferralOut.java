package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;

/**
 * A deferral as it goes back.
 *
 * @param against the card it is granted against
 * @param granted the day it was granted
 * @param by the engineer who granted it, or last tightened it
 * @param atUsage what the tail had flown when it was granted
 * @param limit the limit as the manual prints it
 * @param due where the limit lands
 * @param reInspectWith how the damage is looked at again
 * @param reason why, if anybody wrote it down
 */
public record DeferralOut(
        String against,
        LocalDate granted,
        String by,
        UsageOut atUsage,
        String limit,
        DueOut due,
        String reInspectWith,
        String reason) {}
