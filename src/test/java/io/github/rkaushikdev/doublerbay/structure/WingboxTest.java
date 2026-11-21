package io.github.rkaushikdev.doublerbay.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Rib;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Spar;
import io.github.rkaushikdev.doublerbay.grid.Surface;
import io.github.rkaushikdev.doublerbay.grid.WingBay;
import io.github.rkaushikdev.doublerbay.grid.WingPoint;
import io.github.rkaushikdev.doublerbay.grid.WingStation;
import io.github.rkaushikdev.doublerbay.grid.Zone;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WingboxTest {

    private static final Map<Spar, String> FRACTIONS = Map.of(Spar.FRONT, "0.15", Spar.REAR, "0.65");

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static List<RibStation> ribs(Side side) {
        List<RibStation> found = new ArrayList<>();
        for (int number = 1; number < 14; number++) {
            found.add(new RibStation(
                    new Rib(number, side),
                    WingStation.ofInches(String.valueOf(60 + (number - 1) * 40)),
                    inch(String.valueOf(200 - (number - 1) * 10))));
        }
        return found;
    }

    private static List<RibStation> ribs() {
        return ribs(Side.LEFT);
    }

    private static WingGrid grid(Side side) {
        return new WingGrid(side, ribs(side), FRACTIONS);
    }

    private static WingGrid grid() {
        return grid(Side.LEFT);
    }

    private static WingPoint point(int rib, String outboard, Spar spar, String aft, Surface surface) {
        return new WingPoint(new Rib(rib, Side.LEFT), inch(outboard), spar, inch(aft), surface);
    }

    private static WingPoint point(int rib) {
        return point(rib, "20.000", Spar.FRONT, "10.000", Surface.LOWER);
    }

    private static WingPoint point(Surface surface) {
        return point(1, "20.000", Spar.FRONT, "10.000", surface);
    }

    private static WingPoint point() {
        return point(1);
    }

    private static WingPanel panel(int sequence, int inboard, int outboard, Surface surface) {
        return new WingPanel(
                new PanelId(57, 10, sequence, "L"),
                new Rib(inboard, Side.LEFT),
                new Rib(outboard, Side.LEFT),
                Spar.FRONT,
                Spar.REAR,
                surface,
                new Zone(531),
                surface == Surface.LOWER ? PanelKind.WING_LOWER : PanelKind.WING_UPPER,
                Material.lookup("2024-T351"),
                inch("0.160"));
    }

    private static WingPanel panel() {
        return panel(22, 1, 5, Surface.LOWER);
    }

    private static WingBuild build(int sequence, int inboard, int outboard, Surface surface) {
        return new WingBuild(panel(sequence, inboard, outboard, surface), ThicknessMap.flat(inch("0.160")));
    }

    private static WingBuild build() {
        return build(22, 1, 5, Surface.LOWER);
    }

    private static WingBox box() {
        return new WingBox(
                "V320-200",
                Map.of(Side.LEFT, grid(Side.LEFT), Side.RIGHT, grid(Side.RIGHT)),
                List.of(build(), build(23, 5, 9, Surface.LOWER), build(32, 1, 5, Surface.UPPER)));
    }

    @Test
    void aRibStationCarriesWhereItIsAndHowLongTheChordIs() {
        RibStation entry = ribs().get(0);
        assertThat(entry).hasToString("RIB1L at WS 60.000, chord 200.000");
    }

    @Test
    void aRibWithNoChordIsRefused() {
        assertThatThrownBy(() -> new RibStation(new Rib(1, Side.LEFT), WingStation.ofInches("60"), inch("0")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has no chord");
    }

    @Test
    void aGridNeedsTwoRibsAndBothSpars() {
        assertThatThrownBy(() -> new WingGrid(Side.LEFT, ribs().subList(0, 1), FRACTIONS))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at least two ribs");
        assertThatThrownBy(() -> new WingGrid(Side.LEFT, ribs(), Map.of(Spar.FRONT, "0.15")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("places the rear spar");
    }

    @Test
    void ribsAreListedInboardToOutboard() {
        assertThatThrownBy(() -> new WingGrid(Side.LEFT, ribs().reversed(), FRACTIONS))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("inboard to outboard");
    }

    @Test
    void everyRibOnAGridBelongsToThatWing() {
        assertThatThrownBy(() -> new WingGrid(Side.RIGHT, ribs(Side.LEFT), FRACTIONS))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("belongs to that wing");
    }

    @Test
    void aRibOffTheGridIsALookupMiss() {
        assertThatThrownBy(() -> grid().stationOf(new Rib(30, Side.LEFT)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not on this wing grid");
    }

    @Test
    void theChordAtARibIsTheChordOfThatRib() {
        assertThat(grid().chordAt(inch("60.000"))).isEqualTo(inch("200.000"));
        assertThat(grid().chordOf(new Rib(2, Side.LEFT))).isEqualTo(inch("190.000"));
    }

    @Test
    void theChordBetweenRibsIsStraightLined() {
        assertThat(grid().chordAt(inch("80.000"))).isEqualTo(inch("195.000"));
        assertThat(grid().chordAt(inch("90.000"))).isEqualTo(inch("192.500"));
    }

    @Test
    void theChordAtTheLastRibIsStillAChord() {
        assertThat(grid().chordAt(inch("540.000"))).isEqualTo(inch("80.000"));
    }

    @Test
    void aSpanOffTheWingHasNoChord() {
        assertThatThrownBy(() -> grid().chordAt(inch("541.000")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is off the ribs");
    }

    @Test
    void theSparsMoveAftAsTheChordGrows() {
        assertThat(grid().sparAt(Spar.FRONT, inch("60.000"))).isEqualTo(inch("30.000"));
        assertThat(grid().sparAt(Spar.FRONT, inch("540.000"))).isEqualTo(inch("12.000"));
        assertThat(grid().sparAt(Spar.REAR, inch("60.000"))).isEqualTo(inch("130.000"));
    }

    @Test
    void aSparTheWingDoesNotCarryIsALookupMiss() {
        assertThatThrownBy(() -> grid().sparAt(Spar.MID, inch("60.000")))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("has no mid spar");
    }

    @Test
    void aWrittenWingPointResolvesToSpanAndChord() {
        Offset here = grid().absolute(point());
        assertThat(here.around()).isEqualTo(inch("80.000"));
        assertThat(here.along()).isEqualTo(inch("39.250"));
    }

    @Test
    void aPointFallsInTheRibBayThatBracketsIt() {
        WingBay bay = grid().bayOf(point());
        assertThat(bay.inboard()).isEqualTo(new Rib(1, Side.LEFT));
        assertThat(bay.outboard()).isEqualTo(new Rib(2, Side.LEFT));
        assertThat(bay.isTensionCritical()).isTrue();
    }

    @Test
    void theMarginIsTheNearestOfTheTwoRibsAndTwoSpars() {
        WingMargin margin = grid().marginOf(point());
        assertThat(margin.member()).isSameAs(Spar.FRONT);
        assertThat(margin.distance()).isEqualTo(inch("10.000"));
    }

    @Test
    void theMarginCanBeToARibInstead() {
        WingMargin margin = grid().marginOf(point(1, "4.000", Spar.FRONT, "30.000", Surface.LOWER));
        assertThat(margin.member()).isEqualTo(new Rib(1, Side.LEFT));
        assertThat(margin.distance()).isEqualTo(inch("4.000"));
    }

    @Test
    void theMarginCanBeToTheRearSpar() {
        WingMargin margin = grid().marginOf(point(1, "20.000", Spar.REAR, "2.000", Surface.LOWER));
        assertThat(margin.member()).isSameAs(Spar.REAR);
        assertThat(margin.distance()).isEqualTo(inch("2.000"));
    }

    @Test
    void aWingBoxCarriesBothWings() {
        assertThat(box()).hasToString("V320-200 wing box, 3 panels");
        assertThat(box().gridFor(Side.LEFT).side()).isSameAs(Side.LEFT);
        assertThatThrownBy(() -> new WingBox("V320-200", Map.of(Side.LEFT, grid()), List.of(build())))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has both wings on it");
    }

    @Test
    void twoWingPanelsWithTheSamePartNumberAreRefused() {
        assertThatThrownBy(() -> new WingBox(
                        "V320-200", Map.of(Side.LEFT, grid(), Side.RIGHT, grid(Side.RIGHT)), List.of(build(), build())))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("same part number");
    }

    @Test
    void aWingPanelIsFoundByItsPartNumber() {
        assertThat(box().buildFor(new PanelId(57, 10, 23, "L")).panel().inboard())
                .isEqualTo(new Rib(5, Side.LEFT));
        assertThatThrownBy(() -> box().buildFor(new PanelId(57, 10, 99, "L")))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not a wing panel");
    }

    @Test
    void aPointLandsOnThePanelThatCoversItsBayAndSurface() {
        assertThat(box().panelAt(point()).panel().part()).isEqualTo(new PanelId(57, 10, 22, "L"));
        WingPoint upper = point(Surface.UPPER);
        assertThat(box().panelAt(upper).panel().part()).isEqualTo(new PanelId(57, 10, 32, "L"));
    }

    @Test
    void aPointFurtherOutboardLandsOnTheNextPanel() {
        WingPoint outboard = point(6);
        assertThat(box().panelAt(outboard).panel().part()).isEqualTo(new PanelId(57, 10, 23, "L"));
    }

    @Test
    void aPointOnNoWingPanelIsALookupMiss() {
        assertThatThrownBy(() -> box().panelAt(point(10)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("covers");
    }

    @Test
    void theBoxKnowsWhichSurfacesItCarries() {
        assertThat(box().surfaces()).isEqualTo(Set.of(Surface.LOWER, Surface.UPPER));
    }

    @Test
    void aWingMarginReadsBackWhatItIsOff() {
        assertThat(new WingMargin(Spar.FRONT, inch("2.000"))).hasToString("2.000 off front spar");
    }

    @Test
    void aWingBuildWillNotTakeALandThinnerThanItsPanel() {
        assertThatThrownBy(() -> new WingBuild(panel(), ThicknessMap.flat(inch("0.100"))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("thinner than the panel nominal");
    }
}
