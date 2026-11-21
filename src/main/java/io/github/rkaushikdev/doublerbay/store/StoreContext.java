package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import java.time.LocalDate;

/**
 * Building a tail's context off the repositories, in the one place every caller builds it.
 *
 * <p>A card raised against an aeroplane that had not been delivered yet, or one already sold on,
 * would be assessed against a structure definition that was never on it. The register knows the
 * delivery date, so the answer is to refuse rather than to guess, and that refusal lives wherever
 * the context is built, which is here.
 */
public final class StoreContext {

    private StoreContext() {}

    /**
     * The aeroplane as it stood on a day, refusing one that was not flying. The counters come off
     * the log at that day rather than the latest reading, so a March card is never assessed against
     * September counters.
     */
    public static TailContext contextFor(UnitOfWork unit, TypeCatalogue catalogue, Serial serial, LocalDate on) {
        Aircraft aircraft = unit.aircraft().get(serial);
        if (!aircraft.inServiceOn(on)) {
            throw new NotOnFile(serial + " was not in service on " + on);
        }
        return new TailContext(
                aircraft, on, unit.aircraft().logFor(serial).at(on), catalogue.definitionFor(aircraft.series()));
    }
}
