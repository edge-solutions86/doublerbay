package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Every reading posted against one tail.
 *
 * @param serial the tail
 * @param openedOn the day its log was opened
 * @param latest the last reading
 * @param readings every reading, oldest first
 */
public record UtilisationOut(String serial, LocalDate openedOn, ReadingOut latest, List<ReadingOut> readings) {

    public UtilisationOut {
        readings = List.copyOf(readings);
    }
}
