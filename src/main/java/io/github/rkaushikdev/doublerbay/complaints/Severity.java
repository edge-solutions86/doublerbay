package io.github.rkaushikdev.doublerbay.complaints;

/**
 * Whether a complaint stops the thing being used.
 *
 * <p>Blocking means the definition is not fit to disposition damage against. Advisory means
 * somebody should look at it and the desk can carry on meanwhile. There is deliberately no third
 * level, because a middle one turns into a place to put anything nobody wants to argue about.
 */
public enum Severity {
    BLOCKING("blocking"),
    ADVISORY("advisory");

    private final String value;

    Severity(String value) {
        this.value = value;
    }

    /** The word the audit prints. */
    public String value() {
        return value;
    }

    /** Whether a complaint at this level stops work. */
    public boolean stopsWork() {
        return this == BLOCKING;
    }

    /** Blocking sorts first, which alphabetical order would not manage. */
    public int rank() {
        return this == BLOCKING ? 0 : 1;
    }

    @Override
    public String toString() {
        return value;
    }
}
