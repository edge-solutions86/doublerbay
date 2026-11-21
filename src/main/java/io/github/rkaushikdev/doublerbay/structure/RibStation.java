package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Rib;
import io.github.rkaushikdev.doublerbay.grid.WingStation;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.Objects;

/** One rib, where it sits on the span and how long the chord is there. */
public record RibStation(Rib rib, WingStation at, Length chord) {

    /** A rib placed on the span with a chord. */
    public RibStation {
        Objects.requireNonNull(rib, "rib");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(chord, "chord");
        if (chord.mils() <= 0) {
            throw new BadReading(rib + " has no chord");
        }
    }

    /** {@code RIB1L at WS 60.000, chord 200.000}. */
    @Override
    public String toString() {
        return rib + " at " + at + ", chord " + chord.inches().toPlainString();
    }
}
