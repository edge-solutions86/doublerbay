package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Bay;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Objects;

/** A local thickening across one bay, usually where a fitting or an earlier repair sits. */
public record Pad(String name, Bay bay, Length gauge) {

    /** A pad with a name and a thickness. */
    public Pad {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(bay, "bay");
        Objects.requireNonNull(gauge, "gauge");
        if (Text.isBlank(name)) {
            throw new BadReading("a pad has a name");
        }
        if (gauge.mils() <= 0) {
            throw new BadReading(name + ": a pad has a thickness");
        }
    }

    /** {@code fitting pad 0.100 at FR41-FR42 / S-12L-S-13L}. */
    @Override
    public String toString() {
        return name + " " + gauge.inches().toPlainString() + " at " + bay;
    }
}
