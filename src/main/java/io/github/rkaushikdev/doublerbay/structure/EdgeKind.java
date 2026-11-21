package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.util.Literal;

/** What kind of edge a margin measurement was taken to. */
public enum EdgeKind {
    PANEL_EDGE("panel edge"),
    FASTENER_ROW("fastener row"),
    CUTOUT("cutout"),
    INSTALLED_DOUBLER("installed doubler");

    private final String value;

    EdgeKind(String value) {
        this.value = value;
    }

    /** The kind these words name. */
    public static EdgeKind fromValue(String value) {
        for (EdgeKind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid EdgeKind");
    }

    /** The words, {@code panel edge}. */
    public String value() {
        return value;
    }

    /** {@code panel edge}. */
    @Override
    public String toString() {
        return value;
    }
}
