package io.github.rkaushikdev.doublerbay.logging;

import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * One thing worth writing down: what happened, and at most a reference, a kind, a role, an outcome
 * and a detail. Nothing else can go on a line, so nothing else can reach a log file.
 */
public record Line(
        String event,
        Optional<String> reference,
        Optional<String> kind,
        Optional<String> role,
        Optional<String> outcome,
        Optional<String> detail) {

    /** A line that says what happened. */
    public Line {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(detail, "detail");
        if (Text.isBlank(event)) {
            throw new IllegalArgumentException("a log line says what happened");
        }
    }

    /** A line with nothing on it but the event. */
    public Line(String event) {
        this(event, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** A line from whichever fields are given; a null field carries nothing. */
    public static Line of(String event, String reference, String kind, String role, String outcome, String detail) {
        return new Line(
                event,
                Optional.ofNullable(reference),
                Optional.ofNullable(kind),
                Optional.ofNullable(role),
                Optional.ofNullable(outcome),
                Optional.ofNullable(detail));
    }

    /** The same line naming a reference. */
    public Line withReference(String value) {
        return new Line(event, Optional.of(value), kind, role, outcome, detail);
    }

    /** The same line naming a kind. */
    public Line withKind(String value) {
        return new Line(event, reference, Optional.of(value), role, outcome, detail);
    }

    /** The same line naming a role. */
    public Line withRole(String value) {
        return new Line(event, reference, kind, Optional.of(value), outcome, detail);
    }

    /** The same line naming an outcome. */
    public Line withOutcome(String value) {
        return new Line(event, reference, kind, role, Optional.of(value), detail);
    }

    /** The same line carrying a detail. */
    public Line withDetail(String value) {
        return new Line(event, reference, kind, role, outcome, Optional.of(value));
    }

    /** Only the fields that carry something, in a fixed order. */
    public Map<String, String> fields() {
        Map<String, String> held = new LinkedHashMap<>();
        held.put("event", event);
        reference.ifPresent(value -> held.put("reference", value));
        kind.ifPresent(value -> held.put("kind", value));
        role.ifPresent(value -> held.put("role", value));
        outcome.ifPresent(value -> held.put("outcome", value));
        detail.ifPresent(value -> held.put("detail", value));
        held.values().removeIf(String::isEmpty);
        return Collections.unmodifiableMap(held);
    }

    /** {@code event=refused reference=DR-2025-000001 outcome=no}. */
    @Override
    public String toString() {
        return fields().entrySet().stream()
                .map(field -> field.getKey() + "=" + field.getValue())
                .collect(Collectors.joining(" "));
    }
}
