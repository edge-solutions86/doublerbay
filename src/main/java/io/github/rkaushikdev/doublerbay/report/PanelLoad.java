package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.ids.PanelId;
import java.util.Objects;

/**
 * How much one panel is carrying: every repair on it, and how many of them are temporary.
 *
 * <p>A panel with {@link StructureAudit#CROWDED_PANEL} repairs on it is a panel the shop should be
 * arguing about replacing.
 */
public record PanelLoad(PanelId part, int repairs, int temporary) {

    /** A panel and its counts. */
    public PanelLoad {
        Objects.requireNonNull(part, "part");
    }

    /** Whether the panel carries enough repairs to be worth a conversation about replacing it. */
    public boolean isCrowded() {
        return repairs >= StructureAudit.CROWDED_PANEL;
    }

    /** {@code 5310-143L: 3 repairs, 1 temporary}, the last part only when there are any. */
    @Override
    public String toString() {
        String tail = temporary != 0 ? ", " + temporary + " temporary" : "";
        return part + ": " + repairs + " repairs" + tail;
    }
}
