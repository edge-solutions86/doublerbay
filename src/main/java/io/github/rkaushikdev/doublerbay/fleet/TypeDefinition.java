package io.github.rkaushikdev.doublerbay.fleet;

import io.github.rkaushikdev.doublerbay.structure.Airframe;
import io.github.rkaushikdev.doublerbay.structure.WingBox;
import java.util.Objects;

/** One series, with the barrel and the wing that go with it. */
public record TypeDefinition(Series series, Airframe barrel, WingBox wingBox) {

    /** A series and both halves of its structure. */
    public TypeDefinition {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(barrel, "barrel");
        Objects.requireNonNull(wingBox, "wingBox");
    }

    /** The series, then the barrel as it reads: {@code V320-200: V320-200 (61 frames), ...}. */
    @Override
    public String toString() {
        return series + ": " + barrel;
    }
}
