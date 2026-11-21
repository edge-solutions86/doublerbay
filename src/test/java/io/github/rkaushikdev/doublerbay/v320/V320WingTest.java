package io.github.rkaushikdev.doublerbay.v320;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.grid.Rib;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Spar;
import io.github.rkaushikdev.doublerbay.grid.Surface;
import io.github.rkaushikdev.doublerbay.grid.WingMember;
import io.github.rkaushikdev.doublerbay.grid.WingPoint;
import io.github.rkaushikdev.doublerbay.grid.WingStation;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.WingBox;
import io.github.rkaushikdev.doublerbay.structure.WingBuild;
import io.github.rkaushikdev.doublerbay.structure.WingGrid;
import io.github.rkaushikdev.doublerbay.structure.WingMargin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class V320WingTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static WingPoint point(int rib, String outboard, Spar spar, String aft, Surface surface) {
        return new WingPoint(new Rib(rib, Side.LEFT), inch(outboard), spar, inch(aft), surface);
    }

    private static WingPoint point(int rib) {
        return point(rib, "10.000", Spar.FRONT, "10.000", Surface.LOWER);
    }

    private static WingPoint point(Surface surface) {
        return point(1, "10.000", Spar.FRONT, "10.000", surface);
    }

    private static WingPoint point() {
        return point(1);
    }

    @Test
    void theRootRibIsAtTheSideOfBody() {
        assertThat(Wing.stationOfRib(1)).isEqualTo(WingStation.ofInches("70"));
        assertThat(Wing.stationOfRib(2)).isEqualTo(WingStation.ofInches("92"));
        assertThat(Wing.stationOfRib(Wing.RIB_COUNT)).isEqualTo(WingStation.ofInches("620"));
    }

    @Test
    void theChordTapersFromRootToTip() {
        assertThat(Wing.chordAtRib(1)).isEqualTo(inch("240.000"));
        assertThat(Wing.chordAtRib(Wing.RIB_COUNT)).isEqualTo(inch("78.000"));
        assertThat(Wing.chordAtRib(1).isGreaterThan(Wing.chordAtRib(13))
                        && Wing.chordAtRib(13).isGreaterThan(Wing.chordAtRib(Wing.RIB_COUNT)))
                .isTrue();
    }

    @Test
    void theTaperIsAStraightLine() {
        List<Integer> drops = new ArrayList<>();
        for (int number = 1; number < Wing.RIB_COUNT; number++) {
            drops.add(
                    Wing.chordAtRib(number).mils() - Wing.chordAtRib(number + 1).mils());
        }
        assertThat(Collections.max(drops) - Collections.min(drops)).isLessThanOrEqualTo(1);
    }

    @Test
    void theUpperSurfaceIsTheStrongAlloyAndTheLowerIsTheToughOne() {
        assertThat(Wing.materialOf(Surface.UPPER).alloy()).isEqualTo("7075");
        assertThat(Wing.materialOf(Surface.LOWER).alloy()).isEqualTo("2024");
        assertThat(Wing.materialOf(Surface.LOWER).isDamageTolerant()).isTrue();
        assertThat(Wing.materialOf(Surface.UPPER).isDamageTolerant()).isFalse();
    }

    @Test
    void theZonesSeparateHandAndSurface() {
        assertThat(Wing.zoneOf(Surface.LOWER, Side.LEFT, 0)).isNotEqualTo(Wing.zoneOf(Surface.UPPER, Side.LEFT, 0));
        assertThat(Wing.zoneOf(Surface.LOWER, Side.LEFT, 0)).isNotEqualTo(Wing.zoneOf(Surface.LOWER, Side.RIGHT, 0));
        assertThat(Wing.zoneOf(Surface.LOWER, Side.LEFT, 0).isWing()).isTrue();
    }

    @Test
    void theGridCarriesEveryRibOnItsOwnWing() {
        WingGrid left = Wing.grid(Side.LEFT);
        assertThat(left.ribs()).hasSize(Wing.RIB_COUNT);
        assertThat(left.stationOf(new Rib(5, Side.LEFT))).isEqualTo(WingStation.ofInches("158"));
        assertThatThrownBy(() -> left.stationOf(new Rib(5, Side.RIGHT)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not on this wing grid");
    }

    @Test
    void theSparsSitAtTheirFractionOfTheLocalChord() {
        WingGrid left = Wing.grid(Side.LEFT);
        assertThat(left.sparAt(Spar.FRONT, inch("70.000"))).isEqualTo(inch("36.000"));
        assertThat(left.sparAt(Spar.REAR, inch("70.000"))).isEqualTo(inch("156.000"));
        assertThat(left.sparAt(Spar.FRONT, inch("620.000"))).isEqualTo(inch("11.700"));
    }

    @Test
    void theBoxIsPanelledOnBothSurfacesAndBothWings() {
        WingBox box = Wing.wingBox();
        assertThat(box.panels()).hasSize(28);
        assertThat(box.surfaces()).isEqualTo(Set.of(Surface.LOWER, Surface.UPPER));
        List<WingBuild> left = box.panels().stream()
                .filter(build -> build.panel().side() == Side.LEFT)
                .toList();
        assertThat(left).hasSize(14);
    }

    @Test
    void everyWingPanelNumberIsItsOwn() {
        List<PanelId> parts = Wing.wingBox().panels().stream()
                .map(build -> build.panel().part())
                .toList();
        assertThat(new HashSet<>(parts)).hasSameSizeAs(parts);
    }

    @Test
    void theBoxIsSharedRatherThanRebuilt() {
        assertThat(Wing.wingBox()).isSameAs(Wing.wingBox());
    }

    @Test
    void aRootPointLandsOnTheHeaviestPlate() {
        WingBuild build = Wing.wingBox().panelAt(point());
        assertThat(build.panel().nominal()).isEqualTo(inch("0.250"));
        assertThat(build.panel().kind()).isSameAs(PanelKind.WING_LOWER);
    }

    @Test
    void anOutboardPointLandsOnThinnerPlate() {
        WingBuild build = Wing.wingBox().panelAt(point(22));
        assertThat(build.panel().nominal()).isEqualTo(inch("0.100"));
    }

    @Test
    void theTwoSurfacesAreDifferentPanelsAtTheSamePlace() {
        WingBuild lower = Wing.wingBox().panelAt(point());
        WingBuild upper = Wing.wingBox().panelAt(point(Surface.UPPER));
        assertThat(lower.panel().part()).isNotEqualTo(upper.panel().part());
        assertThat(lower.panel().surface()).isSameAs(Surface.LOWER);
        assertThat(upper.panel().surface()).isSameAs(Surface.UPPER);
    }

    @Test
    void aWingPanelIsMachinedWithAPocket() {
        WingBuild build = Wing.wingBox().panelAt(point());
        assertThat(build.thickness().isFlat()).isFalse();
        assertThat(build.thickness().pocket()).isEqualTo(inch("0.150"));
        assertThat(build.thickness().land()).isEqualTo(inch("0.250"));
    }

    @Test
    void theMarginAtTheRootIsMeasuredOffTheNearestMember() {
        WingMargin margin = Wing.grid(Side.LEFT).marginOf(point());
        assertThat(margin.member()).isIn(List.<WingMember>of(new Rib(1, Side.LEFT), Spar.FRONT));
        assertThat(margin.distance()).isEqualTo(inch("10.000"));
    }

    @Test
    void aPointPastTheTipRibIsOffTheWing() {
        assertThatThrownBy(() -> Wing.grid(Side.LEFT)
                        .bayOf(point(Wing.RIB_COUNT, "10.000", Spar.FRONT, "10.000", Surface.LOWER)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is off the ribs");
    }
}
