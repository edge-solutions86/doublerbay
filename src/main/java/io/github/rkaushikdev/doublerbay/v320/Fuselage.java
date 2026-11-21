package io.github.rkaushikdev.doublerbay.v320;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Frame;
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
import io.github.rkaushikdev.doublerbay.structure.FastenerRow;
import io.github.rkaushikdev.doublerbay.structure.FrameStation;
import io.github.rkaushikdev.doublerbay.structure.FuselageGrid;
import io.github.rkaushikdev.doublerbay.structure.FuselagePanel;
import io.github.rkaushikdev.doublerbay.structure.Joint;
import io.github.rkaushikdev.doublerbay.structure.JointKind;
import io.github.rkaushikdev.doublerbay.structure.Material;
import io.github.rkaushikdev.doublerbay.structure.PanelBuild;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.StringerLine;
import io.github.rkaushikdev.doublerbay.structure.ThicknessMap;
import io.github.rkaushikdev.doublerbay.util.Literal;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * The V320 barrel, as data.
 *
 * <p>Sixty one frames from the nose bulkhead at station 178 to the pressure dome at 1328, twenty
 * inches apart everywhere except through the wing box where the production splices force a shorter
 * pitch. Twenty five stringers a side, from the crown down to the keel, spaced round a seventy four
 * inch radius.
 *
 * <p>The panels are laid out the way the type certificate holder cuts them: two frame bays long and
 * four stringer bays deep, gauge by section and by band, heavier through the centre section where the
 * wing goes in and lighter in the tail cone where the barrel is not pressurised. Longitudinal lap
 * joints run at S-8 and S-16 on both hands and there is a circumferential butt joint at each section
 * splice.
 *
 * <p>Not one of these numbers is guessed. They are the ones the desk reads off the structural repair
 * manual, transcribed once here so that nothing downstream has to carry a copy of them.
 */
public final class Fuselage {

    /** The type the definition is for. */
    public static final String MODEL = "V320-200";

    /** The barrel's radius. */
    public static final Length BARREL_RADIUS = Length.ofInches("74.000");

    /** The station of the nose bulkhead frame, in inches. */
    public static final int NOSE_STATION = 178;

    /** Frames on the standard series barrel. */
    public static final int FRAME_COUNT = 61;

    /**
     * The frames whose pitch aft is the wing box pitch. Frame pitch is twenty inches except across
     * the wing box, where the centre section splices sit at fifteen so the splice straps land on
     * frames.
     */
    public static final List<Integer> WING_BOX_FRAMES = run(24, 34);

    /** The frame pitch everywhere but the wing box, in inches. */
    public static final int STANDARD_PITCH = 20;

    /** The frame pitch across the wing box, in inches. */
    public static final int WING_BOX_PITCH = 15;

    /**
     * Arc from the crown to each stringer, worked round the seventy four inch radius. The spacing
     * opens up below the floor line because the keel beam takes the place of two stringers.
     */
    public static final List<String> STRINGER_ARCS = List.of(
            "0.000", "9.100", "18.200", "27.300", "36.400", "45.500", "54.600", "63.700", "72.800", "81.900", "91.000",
            "100.100", "109.200", "118.300", "127.400", "136.500", "145.600", "154.700", "163.800", "174.400",
            "185.000", "195.600", "206.200", "216.800", "227.400");

    /** The stringers the longitudinal lap joints run along, on both hands. */
    public static final List<Integer> LAP_STRINGERS = List.of(8, 16);

    /** The frames the circumferential butt joints run around, one at each section splice. */
    public static final List<Integer> SPLICE_FRAMES = List.of(10, 24, 34, 48);

    /** The stringers of the crown band. */
    public static final List<Integer> CROWN_BAND = run(0, 4);

    /** The stringers of the upper band. */
    public static final List<Integer> UPPER_BAND = run(4, 12);

    /** The stringers of the lower band. */
    public static final List<Integer> LOWER_BAND = run(12, 20);

    /** The stringers of the keel band. */
    public static final List<Integer> KEEL_BAND = run(20, 25);

    /** Frame bays a barrel panel spans. */
    public static final int PANEL_FRAME_SPAN = 2;

    /** Stringer bays a barrel panel spans. */
    public static final int PANEL_STRINGER_SPAN = 4;

    /**
     * Gauge by section and band. Heavier through the centre where the wing box loads come in, lighter
     * aft of the rear pressure bulkhead.
     */
    public static final Map<SectionBand, String> GAUGES = gauges();

    /** The last frame of the forward section, which is fixed. */
    public static final int FORWARD_LAST = 20;

    /**
     * The last frame of the centre section, which is fixed. The aft split floats, because the short
     * series loses its frames out of the tail cone and nowhere else.
     */
    public static final int CENTRE_LAST = 40;

    /** The first frame the cabin windows run from. */
    public static final int WINDOW_FIRST_FRAME = 12;

    /** How many frames short of the last the cabin windows stop, before the tail cone starts. */
    public static final int WINDOW_TAIL_MARGIN = 13;

    private static final int BARRELS_KEPT = 4;

    private static final Map<Integer, Airframe> BUILT = new LinkedHashMap<>(BARRELS_KEPT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Airframe> eldest) {
            return size() > BARRELS_KEPT;
        }
    };

    /** A section and a band of the barrel, which is what a gauge is called out by. */
    public record SectionBand(String section, String band) {

        /** A section, {@code forward}, {@code centre} or {@code aft}, and a band. */
        public SectionBand {
            Objects.requireNonNull(section, "section");
            Objects.requireNonNull(band, "band");
        }
    }

    /** One section of the barrel and the frames it runs from, up to but not including its last. */
    public record Section(String name, int first, int last) {

        /** A named run of frames. */
        public Section {
            Objects.requireNonNull(name, "name");
        }
    }

    private Fuselage() {}

    private static List<Integer> run(int first, int end) {
        return IntStream.range(first, end).boxed().toList();
    }

    private static Map<SectionBand, String> gauges() {
        Map<SectionBand, String> held = new LinkedHashMap<>();
        held.put(new SectionBand("forward", "crown"), "0.050");
        held.put(new SectionBand("forward", "upper"), "0.040");
        held.put(new SectionBand("forward", "lower"), "0.050");
        held.put(new SectionBand("forward", "keel"), "0.063");
        held.put(new SectionBand("centre", "crown"), "0.063");
        held.put(new SectionBand("centre", "upper"), "0.050");
        held.put(new SectionBand("centre", "lower"), "0.063");
        held.put(new SectionBand("centre", "keel"), "0.080");
        held.put(new SectionBand("aft", "crown"), "0.040");
        held.put(new SectionBand("aft", "upper"), "0.040");
        held.put(new SectionBand("aft", "lower"), "0.050");
        held.put(new SectionBand("aft", "keel"), "0.063");
        return Collections.unmodifiableMap(held);
    }

    /** Walk the pitch table forward so the wing box shortening accumulates properly. */
    public static Station stationOfFrame(int number) {
        int at = NOSE_STATION;
        for (int frame = 1; frame < number; frame++) {
            at += WING_BOX_FRAMES.contains(frame) ? WING_BOX_PITCH : STANDARD_PITCH;
        }
        return Station.ofInches(String.valueOf(at));
    }

    /** The three sections of the standard series barrel. */
    public static List<Section> sectionBounds() {
        return sectionBounds(FRAME_COUNT);
    }

    /** The three sections of a barrel of this many frames. */
    public static List<Section> sectionBounds(int frameCount) {
        return List.of(
                new Section("forward", 1, FORWARD_LAST),
                new Section("centre", FORWARD_LAST, CENTRE_LAST),
                new Section("aft", CENTRE_LAST, frameCount));
    }

    /** The section a frame is in on the standard series barrel. */
    public static String sectionOf(int frameNumber) {
        return sectionOf(frameNumber, FRAME_COUNT);
    }

    /** The section a frame is in on a barrel of this many frames. Past the last frame is aft. */
    public static String sectionOf(int frameNumber, int frameCount) {
        for (Section section : sectionBounds(frameCount)) {
            if (section.first() <= frameNumber && frameNumber < section.last()) {
                return section.name();
            }
        }
        return "aft";
    }

    /** The band a stringer is in, crown to keel. */
    public static String bandOf(int stringerNumber) {
        if (CROWN_BAND.contains(stringerNumber)) {
            return "crown";
        }
        if (UPPER_BAND.contains(stringerNumber)) {
            return "upper";
        }
        if (LOWER_BAND.contains(stringerNumber)) {
            return "lower";
        }
        return "keel";
    }

    /** What the limit tables key a panel in this band and section on. */
    public static PanelKind kindOf(String band, String section) {
        if (section.equals("aft")) {
            return PanelKind.UNPRESSURISED_SKIN;
        }
        if (band.equals("crown")) {
            return PanelKind.CROWN_SKIN;
        }
        if (band.equals("keel")) {
            return PanelKind.BELLY_SKIN;
        }
        return PanelKind.PRESSURISED_SKIN;
    }

    /** The zone a panel in this band and section is painted with, the hand in the last digit. */
    public static Zone zoneOf(String band, String section, Side side) {
        int hundred = band.equals("crown") || band.equals("upper") ? 200 : 100;
        int ten =
                switch (section) {
                    case "forward" -> 1;
                    case "centre" -> 3;
                    case "aft" -> 5;
                    default -> throw new NoSuchElementException(Literal.of(section));
                };
        int unit = side == Side.LEFT ? 1 : 2;
        return new Zone(hundred + ten * 10 + unit);
    }

    /** What a band is cut from: the keel is the strong alloy, everything above it the tough one. */
    public static Material materialOf(String band) {
        return band.equals("keel") ? Material.lookup("7075-T6 CLAD") : Material.lookup("2024-T3 CLAD");
    }

    /** The standard series barrel's frames and stringers. */
    public static FuselageGrid grid() {
        return grid(FRAME_COUNT);
    }

    /** A barrel's frames and stringers, for this many frames. */
    public static FuselageGrid grid(int frameCount) {
        List<FrameStation> frames = new ArrayList<>();
        for (int number = 1; number <= frameCount; number++) {
            frames.add(new FrameStation(new Frame(number), stationOfFrame(number)));
        }
        List<StringerLine> stringers = new ArrayList<>();
        for (int number = 0; number < STRINGER_ARCS.size(); number++) {
            stringers.add(new StringerLine(
                    number != 0 ? new Stringer(number, Side.LEFT) : new Stringer(0),
                    Length.ofInches(STRINGER_ARCS.get(number))));
        }
        return new FuselageGrid(frames, stringers);
    }

    private static Joint lap(Stringer stringer) {
        return new Joint(
                JointKind.LAP,
                stringer,
                List.of(
                        new FastenerRow(new FastenerSize(5), Length.ofInches("0.750"), Length.ofInches("0.500")),
                        new FastenerRow(new FastenerSize(5), Length.ofInches("0.750"), Length.ofInches("1.375")),
                        new FastenerRow(new FastenerSize(5), Length.ofInches("0.750"), Length.ofInches("2.250"))));
    }

    private static Joint butt(Frame frame) {
        return new Joint(
                JointKind.BUTT,
                frame,
                List.of(
                        new FastenerRow(new FastenerSize(6), Length.ofInches("0.940"), Length.ofInches("0.625")),
                        new FastenerRow(new FastenerSize(6), Length.ofInches("0.940"), Length.ofInches("1.500"))));
    }

    private static ThicknessMap thickness(Length gauge, String section) {
        if (section.equals("aft")) {
            return ThicknessMap.flat(gauge);
        }
        Length pocket = gauge.times(new BigDecimal("0.7"));
        return new ThicknessMap(gauge, pocket, Length.ofInches("1.000"));
    }

    /** A window in each frame bay of the passenger cabin, between S-6 and S-9. */
    private static List<Cutout> windows(Frame forward, Frame aft, Stringer upper, int frameCount) {
        int lastWindow = frameCount - WINDOW_TAIL_MARGIN;
        if (upper.number() != 4 || !(WINDOW_FIRST_FRAME <= forward.number() && forward.number() < lastWindow)) {
            return List.of();
        }
        List<Cutout> found = new ArrayList<>();
        for (int number = forward.number(); number < aft.number(); number++) {
            Length centre = stationOfFrame(number).at().plus(Length.ofInches("10.000"));
            found.add(new Cutout(
                    "W" + number + "L",
                    CutoutKind.WINDOW,
                    new Offset(centre, Length.ofInches("63.700")),
                    Length.ofInches("13.000"),
                    Length.ofInches("9.000"),
                    Length.ofInches("2.000")));
        }
        return found;
    }

    private static List<PanelBuild> panels(Side side, int frameCount) {
        List<PanelBuild> built = new ArrayList<>();
        int sequence = side == Side.LEFT ? 100 : 500;
        int lastStringerNumber = STRINGER_ARCS.size() - 1;
        for (int firstFrame = 1; firstFrame < frameCount; firstFrame += PANEL_FRAME_SPAN) {
            int lastFrame = Math.min(firstFrame + PANEL_FRAME_SPAN, frameCount);
            if (lastFrame <= firstFrame) {
                continue;
            }
            for (int firstStringer = 0; firstStringer < lastStringerNumber; firstStringer += PANEL_STRINGER_SPAN) {
                int lastStringer = Math.min(firstStringer + PANEL_STRINGER_SPAN, lastStringerNumber);
                String section = sectionOf(firstFrame, frameCount);
                String band = bandOf(firstStringer);
                Length gauge = Length.ofInches(GAUGES.get(new SectionBand(section, band)));
                Stringer upper = firstStringer != 0 ? new Stringer(firstStringer, side) : new Stringer(0);
                FuselagePanel panel = new FuselagePanel(
                        new PanelId(53, 10, sequence, side == Side.LEFT ? "L" : "R"),
                        new Frame(firstFrame),
                        new Frame(lastFrame),
                        upper,
                        new Stringer(lastStringer, side),
                        zoneOf(band, section, side),
                        kindOf(band, section),
                        materialOf(band),
                        gauge);
                List<Joint> joints = new ArrayList<>();
                for (int number : LAP_STRINGERS) {
                    if (firstStringer <= number && number < lastStringer) {
                        joints.add(lap(new Stringer(number, side)));
                    }
                }
                for (int number : SPLICE_FRAMES) {
                    if (firstFrame <= number && number < lastFrame) {
                        joints.add(butt(new Frame(number)));
                    }
                }
                List<Cutout> cutouts = side == Side.LEFT
                        ? windows(new Frame(firstFrame), new Frame(lastFrame), upper, frameCount)
                        : List.of();
                built.add(new PanelBuild(panel, thickness(gauge, section), joints, cutouts, List.of()));
                sequence += 1;
            }
        }
        return built;
    }

    /**
     * A barrel of a given length, built once per length and shared, the last four lengths asked for
     * kept.
     *
     * <p>The three series share a cross section and differ in how many frames go between the wing box
     * and the tail cone, so one definition and a frame count covers all of them.
     */
    public static synchronized Airframe barrelFor(int frameCount) {
        Airframe held = BUILT.get(frameCount);
        if (held != null) {
            return held;
        }
        if (frameCount < CENTRE_LAST + 2) {
            throw new BadReading("a barrel of " + frameCount + " frames has no tail cone");
        }
        List<PanelBuild> panels = new ArrayList<>(panels(Side.LEFT, frameCount));
        panels.addAll(panels(Side.RIGHT, frameCount));
        Airframe built = new Airframe(MODEL + " (" + frameCount + " frames)", grid(frameCount), panels);
        BUILT.put(frameCount, built);
        return built;
    }

    /** The standard series barrel, which is what most of the fleet is. */
    public static Airframe barrel() {
        return barrelFor(FRAME_COUNT);
    }
}
