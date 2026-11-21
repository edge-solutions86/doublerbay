package io.github.rkaushikdev.doublerbay.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A card looked at again, with the new numbers on it.
 *
 * @param foundOn the day it was measured again
 * @param atUsage what the tail had flown that day
 * @param measurements the new numbers, lengths as text
 */
public record RevisionIn(
        @NotNull LocalDate foundOn, @NotNull @Valid UsageIn atUsage, @NotNull Map<String, Object> measurements) {

    public RevisionIn {
        measurements = measurements == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(measurements));
    }
}
