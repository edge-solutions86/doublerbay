package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.complaints.Complaint;
import io.github.rkaushikdev.doublerbay.complaints.Severity;
import io.github.rkaushikdev.doublerbay.grid.Bay;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.JointMember;
import io.github.rkaushikdev.doublerbay.grid.Rib;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.grid.WingBay;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checking the type definition against itself before anybody assesses damage with it.
 *
 * <p>A structure definition is transcribed by hand off a manual, and the two things that go wrong are
 * a panel that covers nothing and two panels that both claim the same bay. Neither shows up as an
 * error. The first makes a damage card come back as no panel found, which somebody will work around
 * by picking the nearest panel. The second makes the same card resolve to different limits depending
 * on which panel the search happened to reach first, which nobody notices at all.
 *
 * <p>So it is worth walking the whole thing once and saying what does not add up. The complaints come
 * out sorted, blocking ones first, and a definition with a blocking complaint in it is not one to
 * disposition damage against. The {@link Complaint} they come back as is shared with the manual
 * audit, which asks the same kind of question about a different document.
 */
public final class Audits {

    private Audits() {}

    private static Stringer sided(Stringer stringer, Side side) {
        if (stringer.isCrown()) {
            return stringer;
        }
        return new Stringer(stringer.number(), side);
    }

    /** Every single bay the grid defines on one hand, forward to aft and crown down. */
    public static List<Bay> gridBays(FuselageGrid grid, Side side) {
        List<Bay> found = new ArrayList<>();
        List<FrameStation> frames = grid.frames();
        List<StringerLine> stringers = grid.stringers();
        for (int frame = 0; frame + 1 < frames.size(); frame++) {
            for (int stringer = 0; stringer + 1 < stringers.size(); stringer++) {
                found.add(new Bay(
                        frames.get(frame).frame(),
                        frames.get(frame + 1).frame(),
                        sided(stringers.get(stringer).stringer(), side),
                        sided(stringers.get(stringer + 1).stringer(), side)));
            }
        }
        return List.copyOf(found);
    }

    private static Map<Bay, Integer> coverage(Airframe airframe) {
        Map<Bay, Integer> counted = new LinkedHashMap<>();
        for (PanelBuild build : airframe.panels()) {
            for (Bay bay : build.panel().bays()) {
                counted.merge(bay, 1, Integer::sum);
            }
        }
        return counted;
    }

    private static List<Complaint> membersOnTheGrid(Airframe airframe) {
        List<Complaint> found = new ArrayList<>();
        for (PanelBuild build : airframe.panels()) {
            FuselagePanel panel = build.panel();
            for (Frame frame : List.of(panel.forward(), panel.aft())) {
                try {
                    airframe.grid().stationOf(frame);
                } catch (NotOnFile missing) {
                    found.add(new Complaint(Severity.BLOCKING, panel.part().toString(), frame + " is not on the grid"));
                }
            }
            for (Stringer stringer : List.of(panel.upper(), panel.lower())) {
                try {
                    airframe.grid().aroundOf(stringer);
                } catch (NotOnFile missing) {
                    found.add(new Complaint(
                            Severity.BLOCKING, panel.part().toString(), stringer + " is not on the grid"));
                }
            }
        }
        return found;
    }

    private static List<Complaint> jointsOnTheirPanel(Airframe airframe) {
        List<Complaint> found = new ArrayList<>();
        for (PanelBuild build : airframe.panels()) {
            FuselagePanel panel = build.panel();
            for (Joint joint : build.joints()) {
                JointMember member = joint.follows();
                boolean onPanel =
                        switch (member) {
                            case Frame frame -> panel.spansFrame(frame);
                            case Stringer stringer -> panel.spansStringer(stringer);
                        };
                if (!onPanel) {
                    found.add(new Complaint(
                            Severity.BLOCKING,
                            panel.part().toString(),
                            joint.kind() + " follows " + member + ", which is not on this panel"));
                }
            }
        }
        return found;
    }

    private static List<Complaint> cutoutsInsideTheirPanel(Airframe airframe) {
        List<Complaint> found = new ArrayList<>();
        for (PanelBuild build : airframe.panels()) {
            FuselagePanel panel = build.panel();
            Length forward;
            Length aft;
            Length upper;
            Length lower;
            try {
                forward = airframe.grid().stationOf(panel.forward()).at();
                aft = airframe.grid().stationOf(panel.aft()).at();
                upper = airframe.grid().aroundOf(panel.upper());
                lower = airframe.grid().aroundOf(panel.lower());
            } catch (NotOnFile alreadyReported) {
                // Already reported as a member off the grid. Nothing to compare to.
                continue;
            }
            for (Cutout cutout : build.cutouts()) {
                boolean insideAlong = forward.isAtMost(cutout.centre().along())
                        && cutout.centre().along().isAtMost(aft);
                boolean insideAround = upper.isAtMost(cutout.centre().around())
                        && cutout.centre().around().isAtMost(lower);
                if (!(insideAlong && insideAround)) {
                    found.add(new Complaint(
                            Severity.ADVISORY,
                            panel.part().toString(),
                            cutout.name() + " is centred outside the panel it is listed on"));
                }
            }
        }
        return found;
    }

    private static List<Complaint> doublersClearOfEachOther(Airframe airframe) {
        List<Complaint> found = new ArrayList<>();
        for (PanelBuild build : airframe.panels()) {
            List<InstalledDoubler> fitted = build.doublers();
            for (int index = 0; index < fitted.size(); index++) {
                InstalledDoubler first = fitted.get(index);
                for (InstalledDoubler second : fitted.subList(index + 1, fitted.size())) {
                    if (first.footprint().overlaps(second.footprint())) {
                        found.add(new Complaint(
                                Severity.ADVISORY,
                                build.panel().part().toString(),
                                first.part() + " and " + second.part() + " share skin"));
                    }
                }
            }
        }
        return found;
    }

    /** Walk a barrel definition and report everything that does not add up, blocking first. */
    public static List<Complaint> auditAirframe(Airframe airframe) {
        List<Complaint> found = membersOnTheGrid(airframe);
        found.addAll(jointsOnTheirPanel(airframe));
        found.addAll(cutoutsInsideTheirPanel(airframe));
        found.addAll(doublersClearOfEachOther(airframe));

        Map<Bay, Integer> covered = coverage(airframe);
        List<Map.Entry<Bay, Integer>> claims = new ArrayList<>(covered.entrySet());
        claims.sort(Comparator.comparing(claim -> claim.getKey().toString()));
        for (Map.Entry<Bay, Integer> claim : claims) {
            if (claim.getValue() > 1) {
                found.add(new Complaint(
                        Severity.BLOCKING, claim.getKey().toString(), claim.getValue() + " panels claim this bay"));
            }
        }
        for (Side side : List.of(Side.LEFT, Side.RIGHT)) {
            for (Bay bay : gridBays(airframe.grid(), side)) {
                if (!covered.containsKey(bay)) {
                    found.add(new Complaint(Severity.ADVISORY, bay.toString(), "no panel covers this bay"));
                }
            }
        }
        return Complaint.sorted(found);
    }

    /** The same walk for a wing definition, on ribs instead of bays. */
    public static List<Complaint> auditWingBox(WingBox box) {
        List<Complaint> found = new ArrayList<>();
        for (WingBuild build : box.panels()) {
            WingPanel panel = build.panel();
            WingGrid grid = box.gridFor(panel.side());
            for (Rib rib : List.of(panel.inboard(), panel.outboard())) {
                try {
                    grid.stationOf(rib);
                } catch (NotOnFile missing) {
                    found.add(new Complaint(
                            Severity.BLOCKING, panel.part().toString(), rib + " is not on the wing grid"));
                }
            }
        }
        Map<RibBay, Integer> claimed = new LinkedHashMap<>();
        for (WingBuild build : box.panels()) {
            WingPanel panel = build.panel();
            for (WingBay bay : panel.bays()) {
                claimed.merge(
                        new RibBay(bay.inboard().toString(), panel.surface().toString()), 1, Integer::sum);
            }
        }
        List<Map.Entry<RibBay, Integer>> claims = new ArrayList<>(claimed.entrySet());
        claims.sort(Map.Entry.comparingByKey(RibBay.ORDER));
        for (Map.Entry<RibBay, Integer> claim : claims) {
            if (claim.getValue() > 1) {
                found.add(new Complaint(
                        Severity.BLOCKING,
                        claim.getKey().rib() + " " + claim.getKey().surface(),
                        claim.getValue() + " panels claim this rib bay"));
            }
        }
        return Complaint.sorted(found);
    }

    /** A rib bay as the wing audit counts claims on it: the inboard rib and the surface. */
    private record RibBay(String rib, String surface) {

        static final Comparator<RibBay> ORDER =
                Comparator.comparing(RibBay::rib).thenComparing(RibBay::surface);
    }
}
