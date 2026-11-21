package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.Objects;

/** One stringer and how far round the barrel it runs, measured from the crown. */
public record StringerLine(Stringer stringer, Length around) {

    /** A stringer placed at an arc from the crown, never above it. */
    public StringerLine {
        Objects.requireNonNull(stringer, "stringer");
        Objects.requireNonNull(around, "around");
        if (around.mils() < 0) {
            throw new BadReading(stringer + " cannot be above the crown");
        }
    }

    /** {@code S-2L at 16.000 round}. */
    @Override
    public String toString() {
        return stringer + " at " + around.inches().toPlainString() + " round";
    }
}
