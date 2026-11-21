package io.github.rkaushikdev.doublerbay.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * A repair as the shop asks for it, which is a hole and a place.
 *
 * @param against the card it answers
 * @param sequence the repair's number in its chapter
 * @param along how long the hole is, in inches
 * @param around how wide the hole is, in inches
 * @param centreAlong where its centre is along the panel, in inches
 * @param centreAround where its centre is around the panel, in inches
 * @param embodied the day it went on
 * @param by who fitted it
 * @param page the manual page it is schemed against, {@code 53-40-01} when left out
 * @param fastenerDash the fastener size asked for, the standard one when left out
 * @param temporary whether it is a stopgap to be replaced
 */
public record EmbodyIn(
        @NotNull String against,
        @NotNull Integer sequence,
        @NotNull String along,
        @NotNull String around,
        @NotNull String centreAlong,
        @NotNull String centreAround,
        @NotNull LocalDate embodied,
        @NotNull String by,
        String page,
        Integer fastenerDash,
        boolean temporary) {

    /** The manual page a repair is schemed against when the shop does not say. */
    public static final String STANDARD_PAGE = "53-40-01";

    public EmbodyIn {
        page = page == null ? STANDARD_PAGE : page;
    }
}
