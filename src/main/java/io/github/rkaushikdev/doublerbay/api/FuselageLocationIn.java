package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Where on the fuselage a card is, as it arrives: a frame and a stringer, and how far off each.
 *
 * @param structure always {@code fuselage}, which is what it is when left out
 * @param frame the frame forward of the damage
 * @param aftOfFrame how far aft of that frame, in inches as written
 * @param stringer the stringer above the damage, nothing for the crown
 * @param belowStringer how far below that stringer, in inches as written
 * @param side {@code L}, {@code R} or {@code C}
 */
public record FuselageLocationIn(
        @Pattern(regexp = "^fuselage$") String structure,
        @NotNull Integer frame,
        @NotNull String aftOfFrame,
        @NotNull Integer stringer,
        @NotNull String belowStringer,
        @NotNull @Pattern(regexp = "^[LRC]$") String side) {

    public FuselageLocationIn {
        structure = structure == null ? "fuselage" : structure;
    }

    /** A fuselage location with nothing said about the structure. */
    public FuselageLocationIn(Integer frame, String aftOfFrame, Integer stringer, String belowStringer, String side) {
        this(null, frame, aftOfFrame, stringer, belowStringer, side);
    }

    /** The point on the airframe. */
    public FuselagePoint toDomain() {
        Side held = Side.fromValue(side);
        return new FuselagePoint(
                new Frame(frame),
                Schemas.inches(aftOfFrame, "offset"),
                stringer != 0 ? new Stringer(stringer, held) : new Stringer(0),
                Schemas.inches(belowStringer, "offset"));
    }
}
