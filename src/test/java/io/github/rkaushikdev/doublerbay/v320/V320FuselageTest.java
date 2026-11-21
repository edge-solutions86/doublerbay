package io.github.rkaushikdev.doublerbay.v320;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Station;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.grid.Zone;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Airframe;
import io.github.rkaushikdev.doublerbay.structure.Cutout;
import io.github.rkaushikdev.doublerbay.structure.FuselageGrid;
import io.github.rkaushikdev.doublerbay.structure.Joint;
import io.github.rkaushikdev.doublerbay.structure.JointKind;
import io.github.rkaushikdev.doublerbay.structure.PanelBuild;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.Resolved;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class V320FuselageTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static FuselagePoint point(int frame, String aft, int stringer, String down) {
        return new FuselagePoint(new Frame(frame), inch(aft), new Stringer(stringer, Side.LEFT), inch(down));
    }

    private static FuselagePoint point() {
        return point(15, "10.000", 6, "0.000");
    }

    private static FuselagePoint atFrame(int frame) {
        return point(frame, "10.000", 6, "0.000");
    }

    @Test
    void theNoseFrameSitsAtTheDatumStation() {
        assertThat(Fuselage.stationOfFrame(1)).isEqualTo(Station.ofInches("178"));
        assertThat(Fuselage.stationOfFrame(2)).isEqualTo(Station.ofInches("198"));
    }

    @Test
    void theWingBoxPitchIsShorterAndItAccumulates() {
        assertThat(Fuselage.stationOfFrame(25)
                        .at()
                        .minus(Fuselage.stationOfFrame(24).at()))
                .isEqualTo(inch("15.000"));
        assertThat(Fuselage.stationOfFrame(24)
                        .at()
                        .minus(Fuselage.stationOfFrame(23).at()))
                .isEqualTo(inch("20.000"));
        assertThat(Fuselage.stationOfFrame(35)
                        .at()
                        .minus(Fuselage.stationOfFrame(34).at()))
                .isEqualTo(inch("20.000"));
    }

    @Test
    void thePressureDomeFrameLandsWhereTheShorteningLeavesIt() {
        assertThat(Fuselage.stationOfFrame(Fuselage.FRAME_COUNT)).isEqualTo(Station.ofInches("1328"));
    }

    @Test
    void theGridCarriesEveryFrameAndStringer() {
        FuselageGrid built = Fuselage.grid();
        assertThat(built.frames()).hasSize(Fuselage.FRAME_COUNT);
        assertThat(built.stringers()).hasSize(Fuselage.STRINGER_ARCS.size());
        assertThat(built.aroundOf(new Stringer(7, Side.LEFT))).isEqualTo(inch("63.700"));
    }

    @Test
    void theSectionsSplitTheBarrelWhereTheSplicesAre() {
        assertThat(Fuselage.sectionOf(1)).isEqualTo("forward");
        assertThat(Fuselage.sectionOf(19)).isEqualTo("forward");
        assertThat(Fuselage.sectionOf(20)).isEqualTo("centre");
        assertThat(Fuselage.sectionOf(39)).isEqualTo("centre");
        assertThat(Fuselage.sectionOf(40)).isEqualTo("aft");
        assertThat(Fuselage.sectionOf(Fuselage.FRAME_COUNT)).isEqualTo("aft");
    }

    @Test
    void theBandsRunCrownToKeel() {
        assertThat(Fuselage.bandOf(0)).isEqualTo("crown");
        assertThat(Fuselage.bandOf(4)).isEqualTo("upper");
        assertThat(Fuselage.bandOf(12)).isEqualTo("lower");
        assertThat(Fuselage.bandOf(20)).isEqualTo("keel");
        assertThat(Fuselage.bandOf(24)).isEqualTo("keel");
    }

    @Test
    void theTailConeIsNotPressurisedAndTheCabinIs() {
        assertThat(Fuselage.kindOf("upper", "aft")).isSameAs(PanelKind.UNPRESSURISED_SKIN);
        assertThat(Fuselage.kindOf("upper", "centre")).isSameAs(PanelKind.PRESSURISED_SKIN);
        assertThat(Fuselage.kindOf("crown", "forward")).isSameAs(PanelKind.CROWN_SKIN);
        assertThat(Fuselage.kindOf("keel", "forward")).isSameAs(PanelKind.BELLY_SKIN);
    }

    @Test
    void theZonesCarryTheHandInTheLastDigit() {
        assertThat(Fuselage.zoneOf("upper", "forward", Side.LEFT)).isEqualTo(new Zone(211));
        assertThat(Fuselage.zoneOf("upper", "forward", Side.RIGHT)).isEqualTo(new Zone(212));
        assertThat(Fuselage.zoneOf("keel", "centre", Side.LEFT)).isEqualTo(new Zone(131));
    }

    @Test
    void theBarrelIsPanelledBothHands() {
        Airframe built = Fuselage.barrel();
        List<PanelBuild> left = built.panels().stream()
                .filter(build -> build.panel().side() == Side.LEFT)
                .toList();
        List<PanelBuild> right = built.panels().stream()
                .filter(build -> build.panel().side() == Side.RIGHT)
                .toList();
        assertThat(left).hasSameSizeAs(right);
        assertThat(built.panels()).hasSize(360);
    }

    @Test
    void everyPanelNumberOnTheBarrelIsItsOwn() {
        List<PanelId> parts = Fuselage.barrel().panels().stream()
                .map(build -> build.panel().part())
                .toList();
        assertThat(new HashSet<>(parts)).hasSameSizeAs(parts);
    }

    @Test
    void theBarrelIsSharedRatherThanRebuilt() {
        assertThat(Fuselage.barrel()).isSameAs(Fuselage.barrel());
    }

    @Test
    void aCabinPointResolvesOntoAPressurisedPanel() {
        Resolved resolved = Fuselage.barrel().resolve(point());
        assertThat(resolved.build().panel().kind()).isSameAs(PanelKind.PRESSURISED_SKIN);
        assertThat(resolved.build().panel().nominal()).isEqualTo(inch("0.040"));
    }

    @Test
    void aTailConePointResolvesOntoAnUnpressurisedPanel() {
        Resolved resolved = Fuselage.barrel().resolve(atFrame(50));
        assertThat(resolved.build().panel().kind()).isSameAs(PanelKind.UNPRESSURISED_SKIN);
        assertThat(resolved.build().thickness().isFlat()).isTrue();
    }

    @Test
    void aCentreSectionPointResolvesOntoHeavierSkin() {
        Resolved resolved = Fuselage.barrel().resolve(atFrame(30));
        assertThat(resolved.build().panel().nominal()).isEqualTo(inch("0.050"));
    }

    @Test
    void theLapJointsAreWhereTheManualSaysTheyAre() {
        for (int number : Fuselage.LAP_STRINGERS) {
            Resolved resolved = Fuselage.barrel().resolve(point(15, "10.000", number, "0.500"));
            Joint joint =
                    resolved.build().jointOn(new Stringer(number, Side.LEFT)).orElse(null);
            assertThat(joint).isNotNull();
            assertThat(joint.kind()).isSameAs(JointKind.LAP);
        }
    }

    @Test
    void aStringerWithNoLapJointCarriesNone() {
        Resolved resolved = Fuselage.barrel().resolve(point(15, "10.000", 6, "0.500"));
        assertThat(resolved.build().jointOn(new Stringer(6, Side.LEFT))).isEmpty();
    }

    @Test
    void aSpliceFrameCarriesAButtJoint() {
        Resolved resolved = Fuselage.barrel().resolve(point(24, "1.000", 6, "0.000"));
        Joint joint = resolved.build().jointOn(new Frame(24)).orElse(null);
        assertThat(joint).isNotNull();
        assertThat(joint.kind()).isSameAs(JointKind.BUTT);
    }

    @Test
    void theCabinWindowsAreOnTheBarrelAndNamedByFrame() {
        Resolved resolved = Fuselage.barrel().resolve(point(20, "10.000", 7, "0.000"));
        Set<String> names =
                resolved.build().cutouts().stream().map(Cutout::name).collect(Collectors.toSet());
        assertThat(names).contains("W20L");
    }

    @Test
    void aPointOffTheEndOfTheBarrelFindsNoPanel() {
        assertThatThrownBy(() -> Fuselage.barrel().resolve(point(Fuselage.FRAME_COUNT, "40.000", 6, "0.000")))
                .isInstanceOfAny(NotOnFile.class, BadReading.class);
    }
}
