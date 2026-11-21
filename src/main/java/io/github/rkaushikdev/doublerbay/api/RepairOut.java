package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A repair as it goes back, with its whole drawing.
 *
 * @param reference the repair number
 * @param part the panel it is on
 * @param against the card it answers
 * @param form how it sits on the skin
 * @param category what it commits the operator to
 * @param sealed whether it is wet installed
 * @param page the manual page it was schemed against
 * @param filler the filler's thickness in inches, for a flush repair
 * @param centreAlong where its centre is along the panel, in inches
 * @param centreAround where its centre is around the panel, in inches
 * @param embodied the day it went on
 * @param removed the day it came off, once it has
 * @param needsInspection whether it carries a repeat inspection
 * @param doubler the drawing
 */
public record RepairOut(
        String reference,
        String part,
        String against,
        String form,
        String category,
        boolean sealed,
        String page,
        Optional<String> filler,
        String centreAlong,
        String centreAround,
        LocalDate embodied,
        Optional<LocalDate> removed,
        boolean needsInspection,
        DoublerOut doubler) {}
