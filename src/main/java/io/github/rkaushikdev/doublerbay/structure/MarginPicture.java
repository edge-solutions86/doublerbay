package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every edge a damage has to keep away from, with the nearest one called out as governing.
 *
 * <p>Edge margin is one number in the manual and four measurements on the aeroplane. There is the
 * free edge of the panel, there is the nearest row of holes, there is whatever cutout is in the
 * neighbourhood, and there is any doubler already riveted on. A limit written as two diameters of
 * edge margin means two diameters from the nearest of all of them, and the one that governs is worth
 * naming because it is what the repair drawing has to move away from.
 *
 * <p>Positions here are in skin coordinates, aft of the datum and round from the crown, which is what
 * the grid hands out. Cutouts and doublers are placed in the same coordinates, so nothing here
 * converts between two frames of reference and nothing here can get that conversion backwards.
 *
 * <p>Rows run outward from the member their joint follows: aft of a frame, down from a stringer.
 * That is the drawing convention and it is stated once, here.
 */
public record MarginPicture(List<EdgeDistance> distances) {

    /** A picture with at least the panel edge in it. */
    public MarginPicture {
        distances = List.copyOf(distances);
        if (distances.isEmpty()) {
            throw new BadReading("a margin picture has at least the panel edge in it");
        }
    }

    /** Take every edge measurement the manual could mean by edge margin. */
    public static MarginPicture measureMargins(FuselageGrid grid, PanelBuild build, FuselagePoint point) {
        Offset here = grid.absolute(point);
        List<EdgeDistance> found = panelEdges(grid, build, here);
        found.addAll(rowEdges(grid, build, here));
        for (Cutout cutout : build.cutouts()) {
            found.add(new EdgeDistance(EdgeKind.CUTOUT, cutout.name(), cutout.edgeDistance(here)));
        }
        for (InstalledDoubler doubler : build.doublers()) {
            found.add(new EdgeDistance(
                    EdgeKind.INSTALLED_DOUBLER,
                    doubler.part(),
                    doubler.footprint().distanceTo(here)));
        }
        return new MarginPicture(found);
    }

    private static List<EdgeDistance> panelEdges(FuselageGrid grid, PanelBuild build, Offset here) {
        FuselagePanel panel = build.panel();
        List<EdgeDistance> found = new ArrayList<>();
        found.add(new EdgeDistance(
                EdgeKind.PANEL_EDGE,
                panel.forward().toString(),
                here.along().minus(grid.stationOf(panel.forward()).at()).abs()));
        found.add(new EdgeDistance(
                EdgeKind.PANEL_EDGE,
                panel.aft().toString(),
                grid.stationOf(panel.aft()).at().minus(here.along()).abs()));
        found.add(new EdgeDistance(
                EdgeKind.PANEL_EDGE,
                panel.upper().toString(),
                here.around().minus(grid.aroundOf(panel.upper())).abs()));
        found.add(new EdgeDistance(
                EdgeKind.PANEL_EDGE,
                panel.lower().toString(),
                grid.aroundOf(panel.lower()).minus(here.around()).abs()));
        return found;
    }

    private static List<EdgeDistance> rowEdges(FuselageGrid grid, PanelBuild build, Offset here) {
        List<EdgeDistance> found = new ArrayList<>();
        for (Joint joint : build.joints()) {
            Length base;
            Length reading;
            switch (joint.follows()) {
                case Frame frame -> {
                    base = grid.stationOf(frame).at();
                    reading = here.along();
                }
                case Stringer stringer -> {
                    base = grid.aroundOf(stringer);
                    reading = here.around();
                }
            }
            for (FastenerRow row : joint.rows()) {
                Length gap = reading.minus(base.plus(row.offset())).abs();
                found.add(new EdgeDistance(EdgeKind.FASTENER_ROW, joint.follows() + " " + row, gap));
            }
        }
        return found;
    }

    /** The nearest edge, the first measured on a tie. */
    public EdgeDistance governing() {
        return nearest(distances).orElseThrow();
    }

    private static Optional<EdgeDistance> nearest(List<EdgeDistance> edges) {
        EdgeDistance best = null;
        for (EdgeDistance edge : edges) {
            if (best == null || edge.distance().isLessThan(best.distance())) {
                best = edge;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The distance to the governing edge. */
    public Length margin() {
        return governing().distance();
    }

    /** The nearest edge of one kind, if any edge of that kind was measured. */
    public Optional<EdgeDistance> toKind(EdgeKind kind) {
        return nearest(distances.stream().filter(edge -> edge.kind() == kind).toList());
    }

    /** Whether the governing edge is at least a required distance away. */
    public boolean clears(Length required) {
        return margin().isAtLeast(required);
    }

    /** {@code 1.500 to W12L (cutout)}. */
    @Override
    public String toString() {
        EdgeDistance governing = governing();
        return governing + " (" + governing.kind() + ")";
    }
}
