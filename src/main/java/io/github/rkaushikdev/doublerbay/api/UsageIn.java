package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import io.github.rkaushikdev.doublerbay.util.Literal;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * A counter reading as it arrives, with its hours as text so they are never a double.
 *
 * @param cycles the flight cycles, nothing or more
 * @param hours the flight hours, as written
 */
public record UsageIn(@NotNull @Min(0) Integer cycles, @NotNull String hours) {

    /** The reading, refusing hours that are not a number. */
    public Usage toDomain() {
        BigDecimal read;
        try {
            read = Decimals.parse(hours);
        } catch (NumberFormatException refused) {
            throw new BadReading(Literal.of(hours) + " is not a count of hours");
        }
        return new Usage(new Cycles(cycles), Hours.ofHours(read));
    }
}
