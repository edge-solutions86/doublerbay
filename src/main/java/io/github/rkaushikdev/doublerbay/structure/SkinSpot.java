package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.Objects;

/** One place on a panel, with the reason it is called what it is called and the skin there. */
public record SkinSpot(Region region, Length thickness, String feature) {

    /** A spot in a region, the thickness there, and the feature that put it in that region. */
    public SkinSpot {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(thickness, "thickness");
        Objects.requireNonNull(feature, "feature");
    }

    /** {@code in open field (bay off S-12L), 0.040}. */
    @Override
    public String toString() {
        return region + " (" + feature + "), " + thickness.inches().toPlainString();
    }
}
