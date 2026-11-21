package io.github.rkaushikdev.doublerbay.fleet;

import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.structure.Airframe;
import io.github.rkaushikdev.doublerbay.structure.WingBox;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Everything about one aeroplane on one day, taken in a single pass.
 *
 * @see TypeCatalogue#contextFor
 */
public record TailContext(Aircraft aircraft, LocalDate on, Usage flown, TypeDefinition definition) {

    /** A tail, a day, what it had flown by then, and its structure. */
    public TailContext {
        Objects.requireNonNull(aircraft, "aircraft");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(flown, "flown");
        Objects.requireNonNull(definition, "definition");
    }

    /** The tail's serial. */
    public Serial serial() {
        return aircraft.serial();
    }

    /** The barrel of the tail's series. */
    public Airframe barrel() {
        return definition.barrel();
    }

    /** The wing of the tail's series. */
    public WingBox wingBox() {
        return definition.wingBox();
    }

    /** How old the airframe is on the day, in years to two places. */
    public BigDecimal ageYears() {
        return aircraft.ageYears(on);
    }

    /** Whether a manual page written for a range answers for this tail. */
    public boolean coveredBy(Effectivity effectivity) {
        return aircraft.coveredBy(effectivity);
    }

    /** {@code N412VD (MSN 5127, V320-200) on 2025-01-01, 13800 FC / 24200.0 FH}. */
    @Override
    public String toString() {
        return aircraft + " on " + on + ", " + flown;
    }
}
