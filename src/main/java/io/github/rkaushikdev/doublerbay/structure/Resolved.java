package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.grid.Bay;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import java.util.Objects;

/**
 * Everything the desk needs about one point, taken in one pass: the bay, the panel, the spot, the
 * nearest member and every edge in reach, all measured off the same resolved point.
 */
public record Resolved(
        FuselagePoint point, Bay bay, PanelBuild build, SkinSpot spot, Margin margin, MarginPicture margins) {

    /** Everything about one point. */
    public Resolved {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(bay, "bay");
        Objects.requireNonNull(build, "build");
        Objects.requireNonNull(spot, "spot");
        Objects.requireNonNull(margin, "margin");
        Objects.requireNonNull(margins, "margins");
    }

    /** The part number of the panel the point is on. */
    public PanelId part() {
        return build.panel().part();
    }

    /** {@code 5310-142L FR41-FR42 / S-2L-S-3L, in open field (bay off S-2L), 0.040}. */
    @Override
    public String toString() {
        return part() + " " + bay + ", " + spot;
    }
}
