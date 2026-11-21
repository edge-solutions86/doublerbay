package io.github.rkaushikdev.doublerbay.support;

import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Station;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.grid.Zone;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Airframe;
import io.github.rkaushikdev.doublerbay.structure.Cutout;
import io.github.rkaushikdev.doublerbay.structure.CutoutKind;
import io.github.rkaushikdev.doublerbay.structure.DoublerForm;
import io.github.rkaushikdev.doublerbay.structure.FastenerRow;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import io.github.rkaushikdev.doublerbay.structure.FrameStation;
import io.github.rkaushikdev.doublerbay.structure.FuselageGrid;
import io.github.rkaushikdev.doublerbay.structure.FuselagePanel;
import io.github.rkaushikdev.doublerbay.structure.InstalledDoubler;
import io.github.rkaushikdev.doublerbay.structure.Joint;
import io.github.rkaushikdev.doublerbay.structure.JointKind;
import io.github.rkaushikdev.doublerbay.structure.Material;
import io.github.rkaushikdev.doublerbay.structure.PanelBuild;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.StringerLine;
import io.github.rkaushikdev.doublerbay.structure.ThicknessMap;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A small stand in for the real V320 structure, for tests that need one.
 *
 * <p>Frames every twenty inches from station 600, stringers every eight inches down from the crown,
 * and three panels on the left hand side with a lap joint, a window and somebody's old doubler
 * between them. Small enough to work out the right answer by hand and complete enough that every
 * lookup has something to find.
 */
public final class Sample {

    /** Inches between two frames. */
    public static final int FRAME_PITCH = 20;

    /** The station of FR40, the first frame on the grid. */
    public static final int FIRST_STATION = 600;

    /** Inches of arc between two stringers. */
    public static final int STRINGER_PITCH = 8;

    /** The stringer the lap joint runs along. */
    public static final Stringer LAP_STRINGER = new Stringer(4, Side.LEFT);

    private Sample() {}

    /** A length in decimal inches. */
    public static Length inch(String text) {
        return Length.ofInches(text);
    }

    /** A spot in barrel coordinates, aft of the datum and round from the crown. */
    public static Offset at(String along, String around) {
        return new Offset(inch(along), inch(around));
    }

    /** FR40 to FR50 at twenty inch pitch from station 600, and the crown down to S-8L at eight. */
    public static FuselageGrid grid() {
        List<FrameStation> frames = new ArrayList<>();
        for (int number = 40; number < 51; number++) {
            frames.add(new FrameStation(
                    new Frame(number), Station.ofInches(String.valueOf(FIRST_STATION + (number - 40) * FRAME_PITCH))));
        }
        List<StringerLine> stringers = new ArrayList<>();
        for (int number = 0; number < 9; number++) {
            stringers.add(new StringerLine(
                    number != 0 ? new Stringer(number, Side.LEFT) : new Stringer(0),
                    inch(String.valueOf(number * STRINGER_PITCH))));
        }
        return new FuselageGrid(frames, stringers);
    }

    /** A point on the left hand, written off a frame and a stringer. */
    public static FuselagePoint point(int frame, String aft, int stringer, String down) {
        return new FuselagePoint(new Frame(frame), inch(aft), new Stringer(stringer, Side.LEFT), inch(down));
    }

    /** FR41 10.000 aft, S-2L 4.000 down: open field on the forward upper panel. */
    public static FuselagePoint point() {
        return point(41, "10.000", 2, "4.000");
    }

    private static FuselagePanel panel(
            int sequence, int forward, int aftFrame, int upper, int lower, String nominal, int zone) {
        return new FuselagePanel(
                new PanelId(53, 10, sequence, "L"),
                new Frame(forward),
                new Frame(aftFrame),
                upper != 0 ? new Stringer(upper, Side.LEFT) : new Stringer(0),
                new Stringer(lower, Side.LEFT),
                new Zone(zone),
                PanelKind.PRESSURISED_SKIN,
                Material.lookup("2024-T3 CLAD"),
                inch(nominal));
    }

    private static FuselagePanel panel(int sequence, int forward, int aftFrame, int upper, int lower) {
        return panel(sequence, forward, aftFrame, upper, lower, "0.040", 231);
    }

    /** A 0.063 land milled down to a 0.040 pocket an inch in from the members. */
    public static ThicknessMap milled() {
        return new ThicknessMap(inch("0.063"), inch("0.040"), inch("1.000"));
    }

    /** A two row lap joint along S-4L. */
    public static Joint lap() {
        return new Joint(
                JointKind.LAP,
                LAP_STRINGER,
                List.of(
                        new FastenerRow(new FastenerSize(5), inch("0.750"), inch("0.500")),
                        new FastenerRow(new FastenerSize(5), inch("0.750"), inch("1.375"))));
    }

    /** A cabin window centred at station 660, twenty inches round from the crown. */
    public static Cutout window() {
        return new Cutout(
                "W12L", CutoutKind.WINDOW, at("660.000", "20.000"), inch("13.000"), inch("9.000"), inch("2.000"));
    }

    /** Somebody's doubler from 2021, on the forward lower panel. */
    public static InstalledDoubler oldPatch() {
        return new InstalledDoubler(
                "RP-53-0007-1",
                DoublerForm.EXTERNAL,
                new Footprint(at("630.000", "44.000"), inch("6.000"), inch("4.000"), inch("1.000")),
                Material.lookup("2024-T3 CLAD"),
                inch("0.050"),
                LocalDate.of(2021, 4, 2));
    }

    /** FR41 to FR43, S-1L to S-4L: milled, with the lap joint and the window. */
    public static PanelBuild forwardUpper() {
        return new PanelBuild(panel(142, 41, 43, 1, 4), milled(), List.of(lap()), List.of(window()), List.of());
    }

    /** FR43 to FR45, S-1L to S-4L: milled, with the lap joint. */
    public static PanelBuild aftUpper() {
        return new PanelBuild(panel(143, 43, 45, 1, 4), milled(), List.of(lap()), List.of(), List.of());
    }

    /** FR41 to FR43, S-4L to S-7L: flat 0.050, with the lap joint and the old doubler. */
    public static PanelBuild forwardLower() {
        return new PanelBuild(
                panel(152, 41, 43, 4, 7, "0.050", 131),
                ThicknessMap.flat(inch("0.050")),
                List.of(lap()),
                List.of(),
                List.of(oldPatch()));
    }

    /** The three panels on the small grid. */
    public static Airframe airframe() {
        return new Airframe("V320-200", grid(), List.of(forwardUpper(), aftUpper(), forwardLower()));
    }

    /** A second doubler put on top of the first one, which is not allowed. */
    public static InstalledDoubler secondPatch() {
        return new InstalledDoubler(
                "RP-53-0009-1",
                DoublerForm.EXTERNAL,
                new Footprint(at("632.000", "44.000"), inch("6.000"), inch("4.000"), inch("1.000")),
                Material.lookup("2024-T3 CLAD"),
                inch("0.050"),
                LocalDate.of(2022, 9, 30));
    }

    /** A panel that claims bays the forward upper panel already claims. */
    public static PanelBuild overlapping() {
        return new PanelBuild(panel(199, 41, 43, 2, 3), milled());
    }
}
