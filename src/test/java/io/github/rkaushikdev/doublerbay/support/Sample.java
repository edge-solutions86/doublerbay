package io.github.rkaushikdev.doublerbay.support;

import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Geometry;
import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.fleet.DiscoverySource;
import io.github.rkaushikdev.doublerbay.fleet.Fleet;
import io.github.rkaushikdev.doublerbay.fleet.Series;
import io.github.rkaushikdev.doublerbay.fleet.Snapshot;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Point;
import io.github.rkaushikdev.doublerbay.grid.Rib;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Spar;
import io.github.rkaushikdev.doublerbay.grid.Station;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.grid.Surface;
import io.github.rkaushikdev.doublerbay.grid.WingPoint;
import io.github.rkaushikdev.doublerbay.grid.Zone;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Usage;
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
import java.util.Objects;

/**
 * A small stand in for the real V320 structure, for tests that need one.
 *
 * <p>Frames every twenty inches from station 600, stringers every eight inches down from the crown,
 * and three panels on the left hand side with a lap joint, a window and somebody's old doubler
 * between them. Small enough to work out the right answer by hand and complete enough that every
 * lookup has something to find.
 *
 * <p>The second half is the real V320 definition, a tail on it, and cards raised against that tail,
 * for tests that need the real numbers: MSN 5127 wearing N412VD, a V320-200 delivered on the twelfth
 * of April 2016, with two readings a year apart (12000 cycles and 21000 hours at the start of 2024,
 * 13800 and 24200 at the start of 2025), and a register holding it.
 *
 * <h2>Raising a card</h2>
 *
 * <p>{@link #card()} raises DR-2025-000001 against MSN 5127: a dent 0.020 deep, 3.000 by 2.000, at
 * {@link #cabinPoint()}, found on {@link #DAY} at a scheduled check, from an impact, at 13900 FC and
 * 24400 FH. The common shapes are overloads:
 *
 * <pre>{@code
 * Sample.card()                                   // the default card
 * Sample.card(2)                                  // DR-2025-000002
 * Sample.card(Sample.lowerWingPoint())            // somewhere else
 * Sample.card(2, Sample.cabinPointOn(10))         // a number and a place
 * Sample.card(2, "5240")                          // a number on another tail
 * Sample.card(1, DamageKind.CRACK, crack)         // another kind, with its measurements
 * Sample.card(1, DamageKind.CRACK, crack, DamageOrigin.FATIGUE)
 * }</pre>
 *
 * <p>Anything else goes through {@link #cardWith()}, which starts from the default card and changes
 * only what it is told to, one method per thing the card carries:
 *
 * <pre>{@code
 * Sample.cardWith().sequence(3).serial("5240").found(day).source(DiscoverySource.WALKAROUND).build()
 * Sample.cardWith().kind(DamageKind.CORROSION).geometry(rot).origin(DamageOrigin.ENVIRONMENT).build()
 * }</pre>
 *
 * <p>The kind, its measurements and its cause are checked together when the card is built, exactly
 * as a finding checks them, so a kind other than a dent needs measurements of that kind and, where an
 * impact cannot have caused it, a cause that could.
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

    /** The serial every test tail carries unless it says otherwise. */
    public static final Serial MSN = new Serial("5127");

    /** The day the desk answers as of, and the day a card is found unless it says otherwise. */
    public static final LocalDate DAY = LocalDate.of(2025, 3, 14);

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

    // The real V320 definition, a tail on it, and cards raised against that tail. Everything above
    // this line is the small hand checkable structure. Everything below it is the transcribed one, for
    // tests that need the real numbers.

    /** A reading of both counters, the hours whole. */
    public static Usage flown(int cycles, long hours) {
        return UtilisationLog.usage(cycles, hours);
    }

    /** A reading of both counters, the hours as decimal text. */
    public static Usage flown(int cycles, String hours) {
        return UtilisationLog.usage(cycles, hours);
    }

    /** MSN 5127, a V320-200. */
    public static Aircraft aircraft() {
        return aircraft("5127");
    }

    /** The test tail under another serial. */
    public static Aircraft aircraft(String serial) {
        return aircraft(serial, Series.STANDARD);
    }

    /** The test tail under another serial and series. */
    public static Aircraft aircraft(String serial, Series series) {
        return new Aircraft(new Serial(serial), new Registration("N412VD"), series, 1140, LocalDate.of(2016, 4, 12));
    }

    /** The two readings on MSN 5127. */
    public static UtilisationLog flyingLog() {
        return flyingLog("5127");
    }

    /** The same two readings, posted against another serial. */
    public static UtilisationLog flyingLog(String serial) {
        return new UtilisationLog(
                new Serial(serial),
                List.of(
                        new Snapshot(LocalDate.of(2024, 1, 1), flown(12000, 21000)),
                        new Snapshot(LocalDate.of(2025, 1, 1), flown(13800, 24200))));
    }

    /** A register holding MSN 5127 and its log. */
    public static Fleet register() {
        return register("5127");
    }

    /** A register holding the test tail under another serial. */
    public static Fleet register(String serial) {
        return register(serial, Series.STANDARD);
    }

    /** A register holding the test tail under another serial and series. */
    public static Fleet register(String serial, Series series) {
        Fleet held = new Fleet();
        held.enrol(aircraft(serial, series), flyingLog(serial));
        return held;
    }

    /** MSN 5127 on {@link #DAY}, resolved onto the real structure. */
    public static TailContext tailContext() {
        return tailContext("5127", DAY);
    }

    /** The test tail under another serial, on {@link #DAY}. */
    public static TailContext tailContext(String serial) {
        return tailContext(serial, DAY);
    }

    /** MSN 5127 on another day. */
    public static TailContext tailContext(LocalDate day) {
        return tailContext("5127", day);
    }

    /** The test tail under another serial, on another day. */
    public static TailContext tailContext(String serial, LocalDate day) {
        return TypeCatalogue.contextFor(register(serial), new TypeCatalogue(), new Serial(serial), day);
    }

    /** A point in the passenger cabin, on ordinary pressurised skin: FR15 10.000 aft, S-6L on the line. */
    public static FuselagePoint cabinPoint() {
        return cabinPoint(15, "10.000", 6, "0.000");
    }

    /** A point on the left hand of the real barrel, written off a frame and a stringer. */
    public static FuselagePoint cabinPoint(int frame, String aft, int stringer, String down) {
        return new FuselagePoint(new Frame(frame), inch(aft), new Stringer(stringer, Side.LEFT), inch(down));
    }

    /** The cabin point moved onto another stringer: FR15 10.000 aft, on the line of S-nL. */
    public static FuselagePoint cabinPointOn(int stringer) {
        return cabinPoint(15, "10.000", stringer, "0.000");
    }

    /** The cabin point moved onto another stringer and down from it: FR15 10.000 aft. */
    public static FuselagePoint cabinPointOn(int stringer, String down) {
        return cabinPoint(15, "10.000", stringer, down);
    }

    /** The cabin point moved to another frame: 10.000 aft of it, on the line of S-6L. */
    public static FuselagePoint cabinPointAtFrame(int frame) {
        return cabinPoint(frame, "10.000", 6, "0.000");
    }

    /** The cabin point moved to another frame and stringer: 10.000 aft, on the stringer line. */
    public static FuselagePoint cabinPointAtFrame(int frame, int stringer) {
        return cabinPoint(frame, "10.000", stringer, "0.000");
    }

    /** A point on the lower skin of the left wing: rib 2, 6.000 outboard, 20.000 aft of the front spar. */
    public static WingPoint lowerWingPoint() {
        return lowerWingPoint(2, "6.000", "20.000");
    }

    /** The lower wing point moved to another rib. */
    public static WingPoint lowerWingPoint(int rib) {
        return lowerWingPoint(rib, "6.000", "20.000");
    }

    /** A point on the lower skin of the left wing, written off a rib and the front spar. */
    public static WingPoint lowerWingPoint(int rib, String outboard, String aft) {
        return new WingPoint(new Rib(rib, Side.LEFT), inch(outboard), Spar.FRONT, inch(aft), Surface.LOWER);
    }

    /** The default card: DR-2025-000001, a dent on MSN 5127. */
    public static Finding card() {
        return cardWith().build();
    }

    /** The default card under another report sequence. */
    public static Finding card(int sequence) {
        return cardWith().sequence(sequence).build();
    }

    /** The default card somewhere else. */
    public static Finding card(Point at) {
        return cardWith().at(at).build();
    }

    /** The default card under another report sequence, somewhere else. */
    public static Finding card(int sequence, Point at) {
        return cardWith().sequence(sequence).at(at).build();
    }

    /** The default card under another report sequence, on another tail. */
    public static Finding card(int sequence, String serial) {
        return cardWith().sequence(sequence).serial(serial).build();
    }

    /** A card of another kind, with the measurements that kind is reported with. */
    public static Finding card(int sequence, DamageKind kind, Geometry geometry) {
        return cardWith().sequence(sequence).kind(kind).geometry(geometry).build();
    }

    /** A card of another kind, with its measurements and its cause. */
    public static Finding card(int sequence, DamageKind kind, Geometry geometry, DamageOrigin origin) {
        return cardWith()
                .sequence(sequence)
                .kind(kind)
                .geometry(geometry)
                .origin(origin)
                .build();
    }

    /** The default card, ready to have anything on it changed before it is built. */
    public static Card cardWith() {
        return new Card();
    }

    /**
     * A card being filled in, starting from the default card. Each method changes one thing the card
     * carries and hands the same card back; {@link #build()} raises it.
     */
    public static final class Card {

        private int sequence = 1;
        private DamageKind kind = DamageKind.DENT;
        private Geometry geometry;
        private Point at;
        private String serial = "5127";
        private LocalDate found = DAY;
        private DamageOrigin origin = DamageOrigin.IMPACT;
        private DiscoverySource source = DiscoverySource.SCHEDULED_CHECK;

        private Card() {}

        /** The report sequence in 2025. */
        public Card sequence(int value) {
            this.sequence = value;
            return this;
        }

        /** The kind of damage. */
        public Card kind(DamageKind value) {
            this.kind = Objects.requireNonNull(value, "kind");
            return this;
        }

        /** The measurements; a null keeps the default dent. */
        public Card geometry(Geometry value) {
            this.geometry = value;
            return this;
        }

        /** Where it is; a null keeps {@link Sample#cabinPoint()}. */
        public Card at(Point value) {
            this.at = value;
            return this;
        }

        /** The serial of the tail it is raised against. */
        public Card serial(String value) {
            this.serial = Objects.requireNonNull(value, "serial");
            return this;
        }

        /** The day it was found. */
        public Card found(LocalDate value) {
            this.found = Objects.requireNonNull(value, "found");
            return this;
        }

        /** What caused it. */
        public Card origin(DamageOrigin value) {
            this.origin = Objects.requireNonNull(value, "origin");
            return this;
        }

        /** Who or what brought it to the desk. */
        public Card source(DiscoverySource value) {
            this.source = Objects.requireNonNull(value, "source");
            return this;
        }

        /** The card, raised at revision one with the counters at 13900 FC and 24400 FH. */
        public Finding build() {
            Geometry measured = geometry != null ? geometry : new Dent(inch("0.020"), inch("3.000"), inch("2.000"));
            return new Finding(
                    new FindingId(2025, sequence),
                    new Serial(serial),
                    kind,
                    measured,
                    at != null ? at : cabinPoint(),
                    found,
                    source,
                    origin,
                    flown(13900, 24400));
        }
    }
}
