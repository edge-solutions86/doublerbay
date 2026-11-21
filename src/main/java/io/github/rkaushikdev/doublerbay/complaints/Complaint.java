package io.github.rkaushikdev.doublerbay.complaints;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One thing an audit found that does not add up: how bad it is, what it is about, and what is
 * wrong with it.
 *
 * <p>Two audits use this, one over the structure definition and one over the manual, and they
 * share the ordering here so the things that stop work come first.
 */
public record Complaint(Severity severity, String about, String says) {

    private static final Comparator<Complaint> ORDER = Comparator.comparingInt(
                    (Complaint complaint) -> complaint.severity().rank())
            .thenComparing(Complaint::about);

    /** A complaint at a level, about something, saying something. */
    public Complaint {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(about, "about");
        Objects.requireNonNull(says, "says");
    }

    /** Blocking first, then by what they are about, so a report reads the same twice. */
    public static List<Complaint> sorted(Iterable<Complaint> complaints) {
        List<Complaint> held = new ArrayList<>();
        complaints.forEach(held::add);
        held.sort(ORDER);
        return List.copyOf(held);
    }

    /** Only the complaints that stop work. */
    public static List<Complaint> blocking(Iterable<Complaint> complaints) {
        List<Complaint> held = new ArrayList<>();
        for (Complaint complaint : complaints) {
            if (complaint.severity().stopsWork()) {
                held.add(complaint);
            }
        }
        return List.copyOf(held);
    }

    /** Whether anything found stops the definition being used. */
    public static boolean isClear(Iterable<Complaint> complaints) {
        return blocking(complaints).isEmpty();
    }

    /** {@code [blocking] 5310-142L: no limit on file}. */
    @Override
    public String toString() {
        return "[" + severity + "] " + about + ": " + says;
    }
}
