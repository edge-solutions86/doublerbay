package io.github.rkaushikdev.doublerbay.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.fleet.DiscoverySource;
import io.github.rkaushikdev.doublerbay.grid.Point;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A damage card as it arrives.
 *
 * @param serial the tail it was found on
 * @param kind what sort of damage it is
 * @param origin what caused it
 * @param source how it was found
 * @param foundOn the day it was found
 * @param atUsage what the tail had flown when it was found
 * @param fuselage where on the fuselage, for a card on the fuselage
 * @param wing where on a wing, for a card on a wing
 * @param measurements the numbers off the card, lengths as text
 * @param note anything else the mechanic wrote
 * @param urgent set only for corrosion the operator judges an airworthiness concern now
 */
public record FindingIn(
        @NotNull String serial,
        @NotNull String kind,
        @NotNull String origin,
        @NotNull String source,
        @NotNull LocalDate foundOn,
        @NotNull @Valid UsageIn atUsage,
        @JsonSetter(nulls = Nulls.AS_EMPTY) Optional<@Valid FuselageLocationIn> fuselage,
        @JsonSetter(nulls = Nulls.AS_EMPTY) Optional<@Valid WingLocationIn> wing,
        @NotNull Map<String, Object> measurements,
        String note,
        boolean urgent) {

    public FindingIn {
        fuselage = fuselage == null ? Optional.empty() : fuselage;
        wing = wing == null ? Optional.empty() : wing;
        measurements = measurements == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(measurements));
        note = note == null ? "" : note;
    }

    /** The card under the number the desk gave it, refusing one that is not on exactly one structure. */
    public Finding toDomain(FindingId reference) {
        if (fuselage.isEmpty() == wing.isEmpty()) {
            throw new BadReading("a card says where it is, on one structure or the other");
        }
        Point point = fuselage.isPresent()
                ? fuselage.get().toDomain()
                : wing.orElseThrow().toDomain();
        DamageKind read = DamageKind.fromValue(kind);
        return new Finding(
                reference,
                new Serial(serial),
                read,
                Schemas.measurementsIn(read, measurements),
                point,
                foundOn,
                DiscoverySource.fromValue(source),
                DamageOrigin.fromValue(origin),
                atUsage.toDomain(),
                1,
                Optional.empty(),
                note);
    }
}
