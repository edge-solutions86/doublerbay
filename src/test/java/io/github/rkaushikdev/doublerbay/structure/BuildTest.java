package io.github.rkaushikdev.doublerbay.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Bay;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.grid.Zone;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildTest {

    private static final Stringer LAP_STRINGER = new Stringer(12, Side.LEFT);

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static Offset at(String along, String around) {
        return new Offset(inch(along), inch(around));
    }

    private static FuselagePanel skin() {
        return new FuselagePanel(
                new PanelId(53, 10, 142, "L"),
                new Frame(41),
                new Frame(44),
                new Stringer(12, Side.LEFT),
                new Stringer(15, Side.LEFT),
                new Zone(231),
                PanelKind.PRESSURISED_SKIN,
                Material.lookup("2024-T3 CLAD"),
                inch("0.040"));
    }

    private static Joint lap() {
        return new Joint(
                JointKind.LAP,
                LAP_STRINGER,
                List.of(
                        new FastenerRow(new FastenerSize(5), inch("0.750"), inch("0.500")),
                        new FastenerRow(new FastenerSize(5), inch("0.750"), inch("1.375"))));
    }

    private static Cutout window() {
        return new Cutout(
                "W12L", CutoutKind.WINDOW, at("20.000", "20.000"), inch("13.000"), inch("9.000"), inch("2.000"));
    }

    private static InstalledDoubler oldPatch() {
        return new InstalledDoubler(
                "RP-53-0007-1",
                DoublerForm.EXTERNAL,
                new Footprint(at("50.000", "6.000"), inch("6.000"), inch("4.000"), inch("1.000")),
                Material.lookup("2024-T3 CLAD"),
                inch("0.050"),
                LocalDate.of(2021, 4, 2));
    }

    private static PanelBuild build() {
        return new PanelBuild(
                skin(),
                new ThicknessMap(inch("0.063"), inch("0.040"), inch("1.000")),
                List.of(lap()),
                List.of(window()),
                List.of(oldPatch()));
    }

    @Test
    void theRegionsRankTightestFirst() {
        assertThat(Region.UNDER_DOUBLER.tighterThan(Region.OPEN_FIELD)).isTrue();
        assertThat(Region.CUTOUT_CORNER.tighterThan(Region.CUTOUT_EDGE)).isTrue();
        assertThat(Region.FASTENER_ROW.tighterThan(Region.JOINT_BAND)).isTrue();
        assertThat(Region.OPEN_FIELD.tighterThan(Region.LAND)).isFalse();
        assertThat(Region.OPEN_FIELD.isOpenField()).isTrue();
    }

    @Test
    void aLandThinnerThanThePanelItBelongsToIsRefused() {
        assertThatThrownBy(() -> build().withThickness(new ThicknessMap(inch("0.032"), inch("0.032"), inch("1.000"))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("thinner than the panel nominal");
    }

    @Test
    void twoCutoutsWithTheSameNameAreRefused() {
        assertThatThrownBy(() -> build().withCutouts(List.of(window(), window())))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("same name");
    }

    @Test
    void openFieldIsWhatIsLeftWhenNothingElseReaches() {
        SkinSpot spot = build().at(at("60.000", "30.000"), LAP_STRINGER, inch("3.000"));
        assertThat(spot.region()).isSameAs(Region.OPEN_FIELD);
        assertThat(spot.thickness()).isEqualTo(inch("0.040"));
        assertThat(spot.feature()).contains("bay off S-12L");
    }

    @Test
    void aSpotOnTheLandReadsTheLandGauge() {
        SkinSpot spot = build().withJoints(List.of()).at(at("60.000", "30.000"), LAP_STRINGER, inch("0.500"));
        assertThat(spot.region()).isSameAs(Region.LAND);
        assertThat(spot.thickness()).isEqualTo(inch("0.063"));
    }

    @Test
    void aSpotOnTheTaperIsCalledTheStep() {
        SkinSpot spot = build().withJoints(List.of()).at(at("60.000", "30.000"), LAP_STRINGER, inch("1.200"));
        assertThat(spot.region()).isSameAs(Region.CHEM_MILL_STEP);
        assertThat(spot.feature()).isEqualTo("chem mill step");
    }

    @Test
    void aFlatPanelHasNoLandAndNoStep() {
        PanelBuild flat = build().withJoints(List.of()).withThickness(ThicknessMap.flat(inch("0.063")));
        assertThat(flat.at(at("60.000", "30.000"), LAP_STRINGER, inch("0")).region())
                .isSameAs(Region.OPEN_FIELD);
    }

    @Test
    void aSpotWithinTwoDiametersOfARowIsOnTheRow() {
        SkinSpot spot = build().at(at("60.000", "30.000"), LAP_STRINGER, inch("0.500"));
        assertThat(spot.region()).isSameAs(Region.FASTENER_ROW);
        assertThat(spot.feature()).contains("lap joint");
    }

    @Test
    void aSpotInTheJointBandButOffEveryRowIsTheBand() {
        SkinSpot spot = build().at(at("60.000", "30.000"), LAP_STRINGER, inch("0.940"));
        assertThat(spot.region()).isSameAs(Region.JOINT_BAND);
        assertThat(spot.feature()).isEqualTo("lap joint");
    }

    @Test
    void aSpotPastTheJointBandIsNotTheJointAnyMore() {
        SkinSpot spot = build().at(at("60.000", "30.000"), LAP_STRINGER, inch("3.000"));
        assertThat(spot.region()).isSameAs(Region.OPEN_FIELD);
    }

    @Test
    void aJointOnAnotherMemberDoesNotReach() {
        Stringer other = new Stringer(14, Side.LEFT);
        SkinSpot spot = build().at(at("60.000", "30.000"), other, inch("0.500"));
        assertThat(spot.region()).isSameAs(Region.LAND);
        assertThat(build().jointOn(other)).isEmpty();
        assertThat(build().jointOn(LAP_STRINGER)).isPresent();
    }

    @Test
    void aCutoutBeatsAFastenerRow() {
        SkinSpot spot = build().at(at("20.000", "20.000"), LAP_STRINGER, inch("0.500"));
        assertThat(spot.region()).isSameAs(Region.CUTOUT_EDGE);
        assertThat(spot.feature()).isEqualTo("W12L");
    }

    @Test
    void aCornerOfACutoutBeatsTheRestOfItsBand() {
        SkinSpot spot = build().at(at("26.000", "24.000"), LAP_STRINGER, inch("3.000"));
        assertThat(spot.region()).isSameAs(Region.CUTOUT_CORNER);
    }

    @Test
    void aSpotOffAFlatSideOfACutoutIsTheEdgeAndNotACorner() {
        SkinSpot spot = build().at(at("27.000", "20.000"), LAP_STRINGER, inch("3.000"));
        assertThat(spot.region()).isSameAs(Region.CUTOUT_EDGE);
    }

    @Test
    void theNearestCutoutIsTheOneThatAnswers() {
        Cutout second = new Cutout(
                "W13L", CutoutKind.WINDOW, at("40.000", "20.000"), inch("13.000"), inch("9.000"), inch("2.000"));
        SkinSpot spot =
                build().withCutouts(List.of(window(), second)).at(at("34.000", "20.000"), LAP_STRINGER, inch("3.0"));
        assertThat(spot.feature()).isEqualTo("W13L");
    }

    @Test
    void anInstalledDoublerBeatsEverythingAndAddsItsOwnGauge() {
        SkinSpot spot = build().at(at("50.000", "6.000"), LAP_STRINGER, inch("0.500"));
        assertThat(spot.region()).isSameAs(Region.UNDER_DOUBLER);
        assertThat(spot.feature()).isEqualTo("RP-53-0007-1");
        assertThat(spot.thickness()).isEqualTo(inch("0.113"));
    }

    @Test
    void theSpotReadsBackWhatItFoundAndHowThickItIs() {
        SkinSpot spot = new SkinSpot(Region.OPEN_FIELD, inch("0.040"), "bay off S-12L");
        assertThat(spot).hasToString("in open field (bay off S-12L), 0.040");
    }

    @Test
    void aPadOnTheBayIsReadThroughTheBuild() {
        Bay bay = new Bay(new Frame(41), new Frame(42), new Stringer(12, Side.LEFT), new Stringer(13, Side.LEFT));
        assertThat(build().thicknessAt(inch("3.000"), bay)).isEqualTo(inch("0.040"));
        assertThat(build().thicknessAt(inch("0.500"))).isEqualTo(inch("0.063"));
    }

    @Test
    void theDoublerAndCutoutLookupsAnswerNothingWhenNothingIsThere() {
        assertThat(build().doublerOver(at("0", "0"))).isEmpty();
        assertThat(build().cutoutOver(at("0", "0"))).isEmpty();
    }
}
