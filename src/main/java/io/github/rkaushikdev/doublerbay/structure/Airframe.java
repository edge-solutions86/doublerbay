package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.grid.Bay;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Zone;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * One aircraft type's barrel, as grid plus panels, and the one call that turns a card into
 * everything about it.
 *
 * <p>The grid knows where the members are but not what panel is on them. The build knows what is on
 * a panel but not where the panel is. This joins the two, so a damage card goes in and the bay, the
 * panel, the spot and the margins come out together, measured off the same resolved point.
 *
 * <p>Together matters. Reading the thickness from one call and the margin from another is how the
 * two end up describing places an inch apart, and an inch is the difference between the pocket floor
 * and the land.
 */
public record Airframe(String model, FuselageGrid grid, List<PanelBuild> panels) {

    /** A named barrel with panels on it, every panel number its own. */
    public Airframe {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(grid, "grid");
        panels = List.copyOf(panels);
        if (Text.isBlank(model)) {
            throw new BadReading("an airframe has a model");
        }
        if (panels.isEmpty()) {
            throw new BadReading(model + ": an airframe has panels on it");
        }
        List<PanelId> parts = panels.stream().map(build -> build.panel().part()).toList();
        if (new HashSet<>(parts).size() != parts.size()) {
            throw new BadReading(model + ": two panels with the same part number");
        }
    }

    /** The panel with this part number. */
    public PanelBuild buildFor(PanelId part) {
        for (PanelBuild build : panels) {
            if (build.panel().part().equals(part)) {
                return build;
            }
        }
        throw new NotOnFile(part + " is not a panel on the " + model);
    }

    /** The single bay a written location falls in. */
    public Bay bayAt(FuselagePoint point) {
        return grid.bayOf(point);
    }

    /** The panel that covers the bay a written location falls in, the first listed if two do. */
    public PanelBuild panelAt(FuselagePoint point) {
        Bay bay = bayAt(point);
        for (PanelBuild build : panels) {
            if (build.panel().covers(bay)) {
                return build;
            }
        }
        throw new NotOnFile("no panel on the " + model + " covers " + bay);
    }

    /** Everything about one written location, in one pass over one resolved point. */
    public Resolved resolve(FuselagePoint point) {
        Bay bay = bayAt(point);
        PanelBuild build = panelAt(point);
        Margin margin = grid.marginOf(point);
        Offset local = grid.absolute(point);
        SkinSpot spot = build.at(local, margin.member(), margin.distance(), bay);
        MarginPicture margins = MarginPicture.measureMargins(grid, build, point);
        return new Resolved(point, bay, build, spot, margin, margins);
    }

    /** The zones the panels say they are in, in zone order. */
    public Set<Zone> zones() {
        Set<Zone> found = new TreeSet<>();
        for (PanelBuild build : panels) {
            found.add(build.panel().zone());
        }
        return Collections.unmodifiableSet(found);
    }

    /** The panels in one zone, in the order they are listed. */
    public List<PanelBuild> inZone(Zone zone) {
        return panels.stream()
                .filter(build -> build.panel().zone().equals(zone))
                .toList();
    }

    /** How many single bays the panels cover between them. */
    public int bayCount() {
        int total = 0;
        for (PanelBuild build : panels) {
            total += build.panel().bayCount();
        }
        return total;
    }

    /** {@code V320-200, 3 panels}. */
    @Override
    public String toString() {
        return model + ", " + panels.size() + " panels";
    }
}
