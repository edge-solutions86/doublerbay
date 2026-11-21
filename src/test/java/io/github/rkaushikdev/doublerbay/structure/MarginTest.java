package io.github.rkaushikdev.doublerbay.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MarginTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static Offset at(String along, String around) {
        return new Offset(inch(along), inch(around));
    }

    private static FuselageGrid grid() {
        List<FrameStation> frames = new ArrayList<>();
        for (int number = 40; number < 46; number++) {
            frames.add(new FrameStation(new Frame(number), Station.ofInches(String.valueOf(600 + (number - 40) * 20))));
        }
        List<StringerLine> stringers = new ArrayList<>();
        for (int number = 0; number < 6; number++) {
            stringers.add(new StringerLine(
                    number != 0 ? new Stringer(number, Side.LEFT) : new Stringer(0), inch(String.valueOf(number * 8))));
        }
        return new FuselageGrid(frames, stringers);
    }

    private static FuselagePanel panel() {
        return new FuselagePanel(
                new PanelId(53, 10, 142, "L"),
                new Frame(41),
                new Frame(43),
                new Stringer(1, Side.LEFT),
                new Stringer(4, Side.LEFT),
                new Zone(231),
                PanelKind.PRESSURISED_SKIN,
                Material.lookup("2024-T3 CLAD"),
                inch("0.040"));
    }

    private static Joint lap() {
        return new Joint(
                JointKind.LAP,
                new Stringer(1, Side.LEFT),
                List.of(new FastenerRow(new FastenerSize(5), inch("0.750"), inch("0.500"))));
    }

    private static Cutout window() {
        return new Cutout(
                "W12L", CutoutKind.WINDOW, at("660.000", "20.000"), inch("13.000"), inch("9.000"), inch("2.000"));
    }

    private static InstalledDoubler oldPatch() {
        return new InstalledDoubler(
                "RP-53-0007-1",
                DoublerForm.EXTERNAL,
                new Footprint(at("630.000", "30.000"), inch("6.000"), inch("4.000"), inch("1.000")),
                Material.lookup("2024-T3 CLAD"),
                inch("0.050"),
                LocalDate.of(2021, 4, 2));
    }

    private static PanelBuild build(List<Joint> joints, List<Cutout> cutouts, List<InstalledDoubler> doublers) {
        return new PanelBuild(
                panel(), new ThicknessMap(inch("0.063"), inch("0.040"), inch("1.000")), joints, cutouts, doublers);
    }

    private static PanelBuild build() {
        return build(List.of(), List.of(), List.of());
    }

    private static FuselagePoint point(int frame, String aft, int stringer, String down) {
        return new FuselagePoint(new Frame(frame), inch(aft), new Stringer(stringer, Side.LEFT), inch(down));
    }

    private static FuselagePoint point() {
        return point(41, "10.000", 2, "4.000");
    }

    @Test
    void thePanelEdgesAreAlwaysMeasured() {
        MarginPicture picture = MarginPicture.measureMargins(grid(), build(), point());
        assertThat(picture.distances()).hasSize(4);
        assertThat(picture.distances()).allMatch(edge -> edge.kind() == EdgeKind.PANEL_EDGE);
    }

    @Test
    void theGoverningEdgeIsTheNearestOne() {
        MarginPicture picture = MarginPicture.measureMargins(grid(), build(), point());
        assertThat(picture.margin()).isEqualTo(inch("10.000"));
        assertThat(picture.governing().to()).isEqualTo("FR41");
    }

    @Test
    void aPointNearTheAftBoundaryGovernsOffThatFrame() {
        MarginPicture picture = MarginPicture.measureMargins(grid(), build(), point(42, "18.000", 2, "4.000"));
        assertThat(picture.governing().to()).isEqualTo("FR43");
        assertThat(picture.margin()).isEqualTo(inch("2.000"));
    }

    @Test
    void aFastenerRowIsMeasuredOutFromTheMemberItFollows() {
        MarginPicture picture = MarginPicture.measureMargins(
                grid(), build(List.of(lap()), List.of(), List.of()), point(41, "10.000", 1, "0.750"));
        EdgeDistance row = picture.toKind(EdgeKind.FASTENER_ROW).orElseThrow();
        assertThat(row.distance()).isEqualTo(inch("0.250"));
        assertThat(picture.governing().kind()).isSameAs(EdgeKind.FASTENER_ROW);
    }

    @Test
    void aCutoutIsMeasuredToItsEdgeAndNotItsCentre() {
        MarginPicture picture = MarginPicture.measureMargins(
                grid(), build(List.of(), List.of(window()), List.of()), point(42, "11.000", 2, "4.000"));
        EdgeDistance cutout = picture.toKind(EdgeKind.CUTOUT).orElseThrow();
        assertThat(cutout.to()).isEqualTo("W12L");
        assertThat(cutout.distance()).isEqualTo(inch("2.500"));
    }

    @Test
    void anInstalledDoublerIsAnEdgeTheNextRepairHasToClear() {
        MarginPicture picture = MarginPicture.measureMargins(
                grid(), build(List.of(), List.of(), List.of(oldPatch())), point(41, "10.000", 3, "3.0"));
        EdgeDistance doubler = picture.toKind(EdgeKind.INSTALLED_DOUBLER).orElseThrow();
        assertThat(doubler.to()).isEqualTo("RP-53-0007-1");
        assertThat(doubler.distance()).isEqualTo(inch("1.000"));
    }

    @Test
    void aKindThatWasNeverMeasuredAnswersNothing() {
        MarginPicture picture = MarginPicture.measureMargins(grid(), build(), point());
        assertThat(picture.toKind(EdgeKind.CUTOUT)).isEmpty();
        assertThat(picture.toKind(EdgeKind.FASTENER_ROW)).isEmpty();
    }

    @Test
    void theNearestOfSeveralEdgesOfOneKindIsTheOneReported() {
        Joint twoRows = new Joint(
                JointKind.LAP,
                new Stringer(1, Side.LEFT),
                List.of(
                        new FastenerRow(new FastenerSize(5), inch("0.750"), inch("0.500")),
                        new FastenerRow(new FastenerSize(5), inch("0.750"), inch("1.500"))));
        MarginPicture picture = MarginPicture.measureMargins(
                grid(), build(List.of(twoRows), List.of(), List.of()), point(41, "10.000", 1, "1.400"));
        EdgeDistance row = picture.toKind(EdgeKind.FASTENER_ROW).orElseThrow();
        assertThat(row.distance()).isEqualTo(inch("0.100"));
    }

    @Test
    void everythingInReachIsMeasuredAtOnce() {
        MarginPicture picture = MarginPicture.measureMargins(
                grid(), build(List.of(lap()), List.of(window()), List.of(oldPatch())), point());
        Set<EdgeKind> kinds =
                picture.distances().stream().map(EdgeDistance::kind).collect(Collectors.toSet());
        assertThat(kinds).isEqualTo(EnumSet.allOf(EdgeKind.class));
    }

    @Test
    void aRequiredMarginIsMetOrItIsNot() {
        MarginPicture picture = MarginPicture.measureMargins(grid(), build(), point());
        assertThat(picture.clears(inch("10.000"))).isTrue();
        assertThat(picture.clears(inch("10.001"))).isFalse();
    }

    @Test
    void aPictureWithNothingInItIsRefused() {
        assertThatThrownBy(() -> new MarginPicture(List.of()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at least the panel edge");
    }

    @Test
    void thePictureReadsBackTheEdgeThatGoverns() {
        MarginPicture picture = new MarginPicture(List.of(new EdgeDistance(EdgeKind.CUTOUT, "W12L", inch("1.500"))));
        assertThat(picture).hasToString("1.500 to W12L (cutout)");
        assertThat(picture.governing()).hasToString("1.500 to W12L");
    }
}
