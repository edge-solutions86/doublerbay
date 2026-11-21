package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Every open card on one tail, assessed panel by panel as of a day.
 *
 * @param serial the tail
 * @param on the day it was assessed as of
 * @param disposition the worst answer on the tail
 * @param panels each panel with an open card on it
 */
public record TailAssessmentOut(String serial, LocalDate on, String disposition, List<PanelAssessmentOut> panels) {

    public TailAssessmentOut {
        panels = List.copyOf(panels);
    }
}
