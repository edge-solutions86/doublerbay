package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Surface;
import io.github.rkaushikdev.doublerbay.grid.WingBay;
import io.github.rkaushikdev.doublerbay.grid.WingPoint;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Both wings of one type, as grids plus panels. */
public record WingBox(String model, Map<Side, WingGrid> grids, List<WingBuild> panels) {

    /** A wing box with both wings on it and every panel number its own. */
    public WingBox {
        Objects.requireNonNull(model, "model");
        grids = Map.copyOf(grids);
        panels = List.copyOf(panels);
        if (!grids.containsKey(Side.LEFT) || !grids.containsKey(Side.RIGHT)) {
            throw new BadReading(model + ": a wing box has both wings on it");
        }
        List<PanelId> parts = panels.stream().map(build -> build.panel().part()).toList();
        if (new HashSet<>(parts).size() != parts.size()) {
            throw new BadReading(model + ": two wing panels with the same part number");
        }
    }

    /** The grid for one wing. */
    public WingGrid gridFor(Side side) {
        WingGrid grid = grids.get(side);
        if (grid == null) {
            throw new NotOnFile(model + " has no " + side + " wing");
        }
        return grid;
    }

    /** The wing panel with this part number. */
    public WingBuild buildFor(PanelId part) {
        for (WingBuild build : panels) {
            if (build.panel().part().equals(part)) {
                return build;
            }
        }
        throw new NotOnFile(part + " is not a wing panel on the " + model);
    }

    /** The panel on the point's surface that covers the rib bay the point falls in. */
    public WingBuild panelAt(WingPoint point) {
        WingGrid grid = gridFor(point.side());
        WingBay bay = grid.bayOf(point);
        for (WingBuild build : panels) {
            if (build.panel().surface() == point.surface()
                    && build.panel().spansRib(bay.inboard())
                    && build.panel().spansRib(bay.outboard())) {
                return build;
            }
        }
        throw new NotOnFile("no " + point.surface() + " panel on the " + model + " covers " + bay);
    }

    /** The surfaces the box has panels on. */
    public Set<Surface> surfaces() {
        Set<Surface> found = EnumSet.noneOf(Surface.class);
        for (WingBuild build : panels) {
            found.add(build.panel().surface());
        }
        return Collections.unmodifiableSet(found);
    }

    /** {@code V320-200 wing box, 3 panels}. */
    @Override
    public String toString() {
        return model + " wing box, " + panels.size() + " panels";
    }
}
