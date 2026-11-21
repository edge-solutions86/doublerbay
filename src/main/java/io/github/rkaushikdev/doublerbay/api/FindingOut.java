package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A damage card as it goes back.
 *
 * @param reference the card's number
 * @param serial the tail it is on
 * @param kind what sort of damage it is
 * @param origin what caused it
 * @param source how it was found
 * @param foundOn the day it was found
 * @param revision which look at the damage this is, the first being one
 * @param atUsage what the tail had flown when it was found
 * @param location where it is
 * @param measurements the numbers off the card, lengths as text
 * @param supersededBy the card that now carries the damage, if one does
 * @param note anything else the mechanic wrote
 */
public record FindingOut(
        String reference,
        String serial,
        String kind,
        String origin,
        String source,
        LocalDate foundOn,
        int revision,
        UsageOut atUsage,
        LocationOut location,
        Map<String, Object> measurements,
        Optional<String> supersededBy,
        String note) {

    public FindingOut {
        measurements = Collections.unmodifiableMap(new LinkedHashMap<>(measurements));
    }
}
