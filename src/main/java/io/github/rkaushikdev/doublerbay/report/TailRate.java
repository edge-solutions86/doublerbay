package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.fleet.Forecast;
import java.util.Objects;

/**
 * The rate a tail's limits are turned into days at, and whether it is the tail's own or the rate the
 * type is planned at.
 */
public record TailRate(Forecast forecast, boolean measured) {

    /** A forecast, measured off the log or not. */
    public TailRate {
        Objects.requireNonNull(forecast, "forecast");
    }
}
