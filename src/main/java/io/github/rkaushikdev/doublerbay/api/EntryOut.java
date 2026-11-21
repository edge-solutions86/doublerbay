package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.Optional;

/**
 * One graded corrosion finding as it goes back.
 *
 * @param part the panel it was found on
 * @param level the level it was graded at
 * @param foundOn the day it was found
 * @param mustBeReported whether it has to go to the authority
 * @param reportBy the last day it can reach the authority, when it has to
 */
public record EntryOut(
        String part, int level, LocalDate foundOn, boolean mustBeReported, Optional<LocalDate> reportBy) {}
