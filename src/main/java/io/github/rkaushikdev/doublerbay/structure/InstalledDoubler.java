package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.time.LocalDate;
import java.util.Objects;

/** A repair patch already on the aeroplane, which the next one has to work around. */
public record InstalledDoubler(
        String part, DoublerForm form, Footprint footprint, Material material, Length gauge, LocalDate fitted) {

    /** A doubler with a part number and a thickness. */
    public InstalledDoubler {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(gauge, "gauge");
        Objects.requireNonNull(fitted, "fitted");
        if (Text.isBlank(part)) {
            throw new BadReading("an installed doubler has a part number");
        }
        if (gauge.mils() <= 0) {
            throw new BadReading(part + ": a doubler has a thickness");
        }
    }

    /** {@code RP-53-0142-1 external 0.050}. */
    @Override
    public String toString() {
        return part + " " + form.value() + " " + gauge.inches().toPlainString();
    }
}
