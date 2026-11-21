package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.List;

/**
 * What one aeroplane's structure is carrying, as it goes back.
 *
 * @param serial the tail
 * @param on the day it was asked for
 * @param totalRepairs every repair fitted to it
 * @param clean whether nothing on it is worth flagging
 * @param panels what each panel carries
 * @param temporary the temporary repairs and how long they have been on
 * @param clashing the pairs of repairs too close together, each as its two numbers
 */
public record AuditOut(
        String serial,
        LocalDate on,
        int totalRepairs,
        boolean clean,
        List<PanelLoadOut> panels,
        List<TemporaryOut> temporary,
        List<List<String>> clashing) {

    public AuditOut {
        panels = List.copyOf(panels);
        temporary = List.copyOf(temporary);
        clashing = clashing.stream().map(List::copyOf).toList();
    }
}
