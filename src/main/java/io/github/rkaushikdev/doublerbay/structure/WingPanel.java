package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Chapter;
import io.github.rkaushikdev.doublerbay.grid.Rib;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Spar;
import io.github.rkaushikdev.doublerbay.grid.Surface;
import io.github.rkaushikdev.doublerbay.grid.WingBay;
import io.github.rkaushikdev.doublerbay.grid.Zone;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One piece of wing skin, bounded by two ribs and two spars, on one surface. */
public record WingPanel(
        PanelId part,
        Rib inboard,
        Rib outboard,
        Spar forward,
        Spar aft,
        Surface surface,
        Zone zone,
        PanelKind kind,
        Material material,
        Length nominal)
        implements Panel {

    /** A wing panel on one wing, bounds the right way round, its kind naming its surface. */
    public WingPanel {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(inboard, "inboard");
        Objects.requireNonNull(outboard, "outboard");
        Objects.requireNonNull(forward, "forward");
        Objects.requireNonNull(aft, "aft");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(nominal, "nominal");
        if (inboard.side() != outboard.side()) {
            throw new BadReading(part + ": a panel does not cross the centreline");
        }
        if (!inboard.isInboardOf(outboard)) {
            throw new BadReading(part + ": " + inboard + " is not inboard of " + outboard);
        }
        if (!forward.isForwardOf(aft)) {
            throw new BadReading(part + ": " + forward + " is not forward of " + aft);
        }
        if (nominal.mils() <= 0) {
            throw new BadReading(part + ": a panel has a thickness");
        }
        PanelKind expected = surface == Surface.UPPER ? PanelKind.WING_UPPER : PanelKind.WING_LOWER;
        if (kind != expected && kind != PanelKind.FAIRING) {
            throw new BadReading(part + ": " + kind + " is not on the " + surface + " surface");
        }
    }

    /** Wing skin is filed under the wings chapter. */
    @Override
    public Chapter chapter() {
        return Chapter.WINGS;
    }

    /** The wing the panel is on. */
    @Override
    public Side side() {
        return inboard.side();
    }

    /** How many rib pitches the panel spans. */
    public int ribPitches() {
        return outboard.number() - inboard.number();
    }

    @Override
    public int bayCount() {
        return ribPitches();
    }

    /** Every rib bay the panel covers, inboard to outboard. */
    public List<WingBay> bays() {
        List<WingBay> found = new ArrayList<>();
        for (int number = inboard.number(); number < outboard.number(); number++) {
            found.add(new WingBay(new Rib(number, side()), new Rib(number + 1, side()), forward, aft, surface));
        }
        return List.copyOf(found);
    }

    /** Whether a rib is on the panel, on its wing, its bounding ribs included. */
    public boolean spansRib(Rib rib) {
        if (rib.side() != side()) {
            return false;
        }
        return inboard.number() <= rib.number() && rib.number() <= outboard.number();
    }

    /** {@code 5710-022L (wing lower)}. */
    @Override
    public String toString() {
        return part + " (" + kind + ")";
    }
}
