package io.github.rkaushikdev.doublerbay.fleet;

import io.github.rkaushikdev.doublerbay.util.Literal;

/** Who or what brought the damage to the desk. */
public enum DiscoverySource {
    WALKAROUND("walkaround"),
    SCHEDULED_CHECK("scheduled check"),
    CREW_REPORT("crew report"),
    GROUND_HANDLING("ground handling report"),
    LIGHTNING_REPORT("lightning strike report"),
    NDT("non destructive test");

    private final String value;

    DiscoverySource(String value) {
        this.value = value;
    }

    /** The words the report form uses. */
    public String value() {
        return value;
    }

    /**
     * The source the report form names with these words.
     *
     * @throws IllegalArgumentException when no source is
     */
    public static DiscoverySource fromValue(String value) {
        for (DiscoverySource source : values()) {
            if (source.value.equals(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid DiscoverySource");
    }

    /** Whether a person saw it and said so, rather than an instrument or a check finding it. */
    public boolean isReportedByAPerson() {
        return this == WALKAROUND || this == CREW_REPORT || this == GROUND_HANDLING || this == LIGHTNING_REPORT;
    }

    /** Sources that come with something that happened, and a time it happened at. */
    public boolean carriesAnEvent() {
        return this == GROUND_HANDLING || this == LIGHTNING_REPORT;
    }

    @Override
    public String toString() {
        return value;
    }
}
