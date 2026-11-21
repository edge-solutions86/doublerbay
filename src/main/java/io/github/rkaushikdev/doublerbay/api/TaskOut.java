package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A repeat inspection as it goes back.
 *
 * @param against the repair that carries it
 * @param part the panel it is on
 * @param method how it is done
 * @param threshold when it first falls due, as the manual prints it
 * @param repeat how often it comes round after that, when it does
 * @param embodied the day the repair went on
 * @param atEmbodiment what the tail had flown when the repair went on
 * @param lastDone the day it was last signed off, if it ever was
 * @param atLastDone what the tail had flown when it was last signed off
 * @param oneOff whether it is a replacement limit that does not come round again
 * @param due when it next falls due, nothing for a one off already done
 */
public record TaskOut(
        String against,
        String part,
        String method,
        String threshold,
        Optional<String> repeat,
        LocalDate embodied,
        UsageOut atEmbodiment,
        Optional<LocalDate> lastDone,
        Optional<UsageOut> atLastDone,
        boolean oneOff,
        Optional<DueOut> due) {}
