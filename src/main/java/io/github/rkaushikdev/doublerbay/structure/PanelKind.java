package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.util.Literal;

/**
 * What the limit tables are keyed on, which is not the same as where a panel is.
 *
 * <p>Pressurised barrel skin, unpressurised tail cone, belly, wing upper, wing lower: the geometry is
 * nearly the same and the numbers are not close.
 */
public enum PanelKind {
    PRESSURISED_SKIN("pressurised skin"),
    UNPRESSURISED_SKIN("unpressurised skin"),
    BELLY_SKIN("belly skin"),
    CROWN_SKIN("crown skin"),
    WING_UPPER("wing upper"),
    WING_LOWER("wing lower"),
    FAIRING("fairing");

    private final String value;

    PanelKind(String value) {
        this.value = value;
    }

    /** The kind these words name. */
    public static PanelKind fromValue(String value) {
        for (PanelKind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid PanelKind");
    }

    /** The words, {@code pressurised skin}. */
    public String value() {
        return value;
    }

    /** Whether the skin holds cabin pressure. */
    public boolean isPressurised() {
        return this == PRESSURISED_SKIN || this == BELLY_SKIN || this == CROWN_SKIN;
    }

    /** Fairings carry nothing. Everything else is structure. */
    public boolean isPrimary() {
        return this != FAIRING;
    }

    /** {@code pressurised skin}. */
    @Override
    public String toString() {
        return value;
    }
}
