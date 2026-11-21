package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A wing panel with what has been done to it. */
public record WingBuild(WingPanel panel, ThicknessMap thickness, List<InstalledDoubler> doublers) {

    /** A build whose land is at least the panel gauge. */
    public WingBuild {
        Objects.requireNonNull(panel, "panel");
        Objects.requireNonNull(thickness, "thickness");
        doublers = List.copyOf(doublers);
        if (thickness.land().isLessThan(panel.nominal())) {
            throw new BadReading(panel.part() + ": the land is thinner than the panel nominal gauge");
        }
    }

    /** A wing panel with nothing on it but its thickness. */
    public WingBuild(WingPanel panel, ThicknessMap thickness) {
        this(panel, thickness, List.of());
    }

    /** The installed doubler a spot is under, if there is one. */
    public Optional<InstalledDoubler> doublerOver(Offset local) {
        for (InstalledDoubler doubler : doublers) {
            if (doubler.footprint().contains(local)) {
                return Optional.of(doubler);
            }
        }
        return Optional.empty();
    }
}
