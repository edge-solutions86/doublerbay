package io.github.rkaushikdev.doublerbay.v320;

import io.github.rkaushikdev.doublerbay.grid.Rib;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Spar;
import io.github.rkaushikdev.doublerbay.grid.Surface;
import io.github.rkaushikdev.doublerbay.grid.WingStation;
import io.github.rkaushikdev.doublerbay.grid.Zone;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Material;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.RibStation;
import io.github.rkaushikdev.doublerbay.structure.ThicknessMap;
import io.github.rkaushikdev.doublerbay.structure.WingBox;
import io.github.rkaushikdev.doublerbay.structure.WingBuild;
import io.github.rkaushikdev.doublerbay.structure.WingGrid;
import io.github.rkaushikdev.doublerbay.structure.WingPanel;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The V320 wing, as data.
 *
 * <p>Twenty six ribs a side, from the side of body at wing station 70 out to the tip rib at 620,
 * twenty two inches apart. The chord runs from two hundred and forty at the root to seventy eight at
 * the tip, which is where the taper in the wing box comes from.
 *
 * <p>The two surfaces are not built the same and it is not a detail. The upper skin is 7075 because
 * it spends the flight in compression and 7075 is the stronger alloy. The lower skin is 2024 because
 * it spends the flight in tension and 2024 is the tougher one, and toughness is what stops a crack in
 * the surface that grows them. The gauges taper outboard on both, from a quarter inch at the root
 * down to a tenth at the tip, because the bending moment does.
 */
public final class Wing {

    /** The type the definition is for. */
    public static final String MODEL = "V320-200";

    /** The wing station of the side of body rib, in inches. */
    public static final int SIDE_OF_BODY = 70;

    /** Inches between two ribs. */
    public static final int RIB_PITCH = 22;

    /** Ribs a side. */
    public static final int RIB_COUNT = 26;

    /** The chord at the side of body rib, in inches. */
    public static final BigDecimal ROOT_CHORD = new BigDecimal("240.000");

    /** The chord at the tip rib, in inches. */
    public static final BigDecimal TIP_CHORD = new BigDecimal("78.000");

    /** How far aft of the leading edge each spar runs, as a fraction of the local chord. */
    public static final Map<Spar, String> SPAR_FRACTIONS = sparFractions();

    /** Rib bays a wing panel spans. */
    public static final int PANEL_RIB_SPAN = 4;

    /**
     * Skin gauge falls outboard because the bending moment does. Called out at the inboard rib of
     * each panel and held constant across it, which is how the plate is ordered.
     */
    public static final List<String> GAUGES = List.of("0.250", "0.220", "0.190", "0.160", "0.125", "0.100");

    /** The machined pocket floor, as a fraction of the plate the panel is cut from. */
    public static final BigDecimal POCKET_FRACTION = new BigDecimal("0.6");

    /** The full thickness land along the members, in inches. */
    public static final String LAND_WIDTH = "1.500";

    private Wing() {}

    private static Map<Spar, String> sparFractions() {
        Map<Spar, String> fractions = new EnumMap<>(Spar.class);
        fractions.put(Spar.FRONT, "0.15");
        fractions.put(Spar.REAR, "0.65");
        return Collections.unmodifiableMap(fractions);
    }

    /** Where a rib sits on the span. */
    public static WingStation stationOfRib(int number) {
        return WingStation.ofInches(String.valueOf(SIDE_OF_BODY + (number - 1) * RIB_PITCH));
    }

    /** The chord at a rib, straight line taper from the root chord to the tip chord. */
    public static Length chordAtRib(int number) {
        BigDecimal span = Decimals.over(BigDecimal.valueOf(number - 1), RIB_COUNT - 1);
        BigDecimal taken = Decimals.times(ROOT_CHORD.subtract(TIP_CHORD, Decimals.CONTEXT), span);
        return Length.ofInches(Decimals.quantize(ROOT_CHORD.subtract(taken, Decimals.CONTEXT), 3));
    }

    /** What a surface is cut from: the tough alloy underneath, the strong one on top. */
    public static Material materialOf(Surface surface) {
        return surface == Surface.LOWER ? Material.lookup("2024-T351") : Material.lookup("7075-T6");
    }

    /** What the limit tables key a surface's panels on. */
    public static PanelKind kindOf(Surface surface) {
        return surface == Surface.LOWER ? PanelKind.WING_LOWER : PanelKind.WING_UPPER;
    }

    /** The zone a panel group is painted with, by surface, wing and how far outboard. */
    public static Zone zoneOf(Surface surface, Side side, int group) {
        int hundred = side == Side.LEFT ? 500 : 600;
        int ten = Math.min(group + 1, 8);
        int unit = surface == Surface.LOWER ? 1 : 3;
        if (side == Side.RIGHT) {
            unit += 1;
        }
        return new Zone(hundred + ten * 10 + unit);
    }

    /** One wing's ribs, chords and spar fractions. */
    public static WingGrid grid(Side side) {
        List<RibStation> ribs = new ArrayList<>();
        for (int number = 1; number <= RIB_COUNT; number++) {
            ribs.add(new RibStation(new Rib(number, side), stationOfRib(number), chordAtRib(number)));
        }
        return new WingGrid(side, ribs, SPAR_FRACTIONS);
    }

    private static ThicknessMap thickness(Length gauge) {
        Length pocket = gauge.times(POCKET_FRACTION);
        return new ThicknessMap(gauge, pocket, Length.ofInches(LAND_WIDTH));
    }

    private static List<WingBuild> panels(Side side) {
        List<WingBuild> built = new ArrayList<>();
        String hand = side == Side.LEFT ? "L" : "R";
        int sequence = side == Side.LEFT ? 20 : 60;
        for (Surface surface : List.of(Surface.LOWER, Surface.UPPER)) {
            int group = 0;
            for (int first = 1; first < RIB_COUNT; first += PANEL_RIB_SPAN, group++) {
                int last = Math.min(first + PANEL_RIB_SPAN, RIB_COUNT);
                if (last <= first) {
                    continue;
                }
                Length gauge = Length.ofInches(GAUGES.get(Math.min(group, GAUGES.size() - 1)));
                WingPanel panel = new WingPanel(
                        new PanelId(57, 10, sequence, hand),
                        new Rib(first, side),
                        new Rib(last, side),
                        Spar.FRONT,
                        Spar.REAR,
                        surface,
                        zoneOf(surface, side, group),
                        kindOf(surface),
                        materialOf(surface),
                        gauge);
                built.add(new WingBuild(panel, thickness(gauge)));
                sequence += 1;
            }
        }
        return built;
    }

    /** Both wings, built once and shared. */
    public static WingBox wingBox() {
        return Built.BOX;
    }

    private static final class Built {

        static final WingBox BOX = build();

        private Built() {}

        private static WingBox build() {
            List<WingBuild> panels = new ArrayList<>(panels(Side.LEFT));
            panels.addAll(panels(Side.RIGHT));
            return new WingBox(MODEL, Map.of(Side.LEFT, grid(Side.LEFT), Side.RIGHT, grid(Side.RIGHT)), panels);
        }
    }
}
