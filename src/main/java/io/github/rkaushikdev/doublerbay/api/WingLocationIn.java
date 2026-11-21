package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.grid.Rib;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Spar;
import io.github.rkaushikdev.doublerbay.grid.Surface;
import io.github.rkaushikdev.doublerbay.grid.WingPoint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Where on a wing a card is, as it arrives: a rib and a spar, how far off each, and which skin.
 *
 * @param structure always {@code wing}, which is what it is when left out
 * @param rib the rib inboard of the damage
 * @param outboardOfRib how far outboard of that rib, in inches as written
 * @param spar the spar forward of the damage
 * @param aftOfSpar how far aft of that spar, in inches as written
 * @param surface the upper or the lower skin
 * @param side {@code L} or {@code R}
 */
public record WingLocationIn(
        @Pattern(regexp = "^wing$") String structure,
        @NotNull Integer rib,
        @NotNull String outboardOfRib,
        @NotNull String spar,
        @NotNull String aftOfSpar,
        @NotNull String surface,
        @NotNull @Pattern(regexp = "^[LR]$") String side) {

    public WingLocationIn {
        structure = structure == null ? "wing" : structure;
    }

    /** A wing location with nothing said about the structure. */
    public WingLocationIn(
            Integer rib, String outboardOfRib, String spar, String aftOfSpar, String surface, String side) {
        this(null, rib, outboardOfRib, spar, aftOfSpar, surface, side);
    }

    /** The point on the airframe. */
    public WingPoint toDomain() {
        return new WingPoint(
                new Rib(rib, Side.fromValue(side)),
                Schemas.inches(outboardOfRib, "offset"),
                Spar.fromValue(spar),
                Schemas.inches(aftOfSpar, "offset"),
                Surface.fromValue(surface));
    }
}
